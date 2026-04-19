import os
import cv2
import numpy as np
import matplotlib.pyplot as plt
import tensorflow as tf
from tensorflow.keras import layers, models
import sys
import io

if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

IMG_SIZE = (256, 256)
BATCH_SIZE = 8  
EPOCHS = 25
DATASET_PATH = r'---' 

def load_data(img_dir, mask_dir):
    images = []
    masks = []
    
    if not os.path.exists(img_dir) or not os.path.exists(mask_dir):
        return np.array([]), np.array([])

    img_names = sorted(os.listdir(img_dir))
    mask_names = sorted(os.listdir(mask_dir))
    
    
    for i in range(len(img_names)):
        img = cv2.imread(os.path.join(img_dir, img_names[i]))
        if img is None: continue
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        img = cv2.resize(img, IMG_SIZE)
        images.append(img / 255.0) 
        
        mask = cv2.imread(os.path.join(mask_dir, mask_names[i]), cv2.IMREAD_GRAYSCALE)
        if mask is None: continue
        mask = cv2.resize(mask, IMG_SIZE)
        mask = np.expand_dims(mask, axis=-1)
        mask = (mask > 0).astype(np.float32) 
        masks.append(mask)
        
    return np.array(images), np.array(masks)

X_train, y_train = load_data(os.path.join(DATASET_PATH, 'images_prepped_train'), 
                             os.path.join(DATASET_PATH, 'annotations_prepped_train'))
X_test, y_test = load_data(os.path.join(DATASET_PATH, 'images_prepped_test'), 
                           os.path.join(DATASET_PATH, 'annotations_prepped_test'))

if len(X_train) == 0:
    sys.exit()

data_gen_args = dict(rotation_range=15,
                     width_shift_range=0.1,
                     height_shift_range=0.1,
                     horizontal_flip=True,
                     fill_mode='nearest')

image_datagen = tf.keras.preprocessing.image.ImageDataGenerator(**data_gen_args)
mask_datagen = tf.keras.preprocessing.image.ImageDataGenerator(**data_gen_args)

seed = 42
image_generator = image_datagen.flow(X_train, batch_size=BATCH_SIZE, seed=seed)
mask_generator = mask_datagen.flow(y_train, batch_size=BATCH_SIZE, seed=seed)
train_generator = zip(image_generator, mask_generator)

def unet():
    inputs = layers.Input((256, 256, 3))
    
    c1 = layers.Conv2D(32, (3, 3), activation='relu', padding='same')(inputs)
    p1 = layers.MaxPooling2D((2, 2))(c1)
    
    c2 = layers.Conv2D(64, (3, 3), activation='relu', padding='same')(p1)
    p2 = layers.MaxPooling2D((2, 2))(c2)
    
    c3 = layers.Conv2D(128, (3, 3), activation='relu', padding='same')(p2)
    
    u4 = layers.Conv2DTranspose(64, (2, 2), strides=(2, 2), padding='same')(c3)
    u4 = layers.concatenate([u4, c2])
    c4 = layers.Conv2D(64, (3, 3), activation='relu', padding='same')(u4)
    
    u5 = layers.Conv2DTranspose(32, (2, 2), strides=(2, 2), padding='same')(c4)
    u5 = layers.concatenate([u5, c1])
    c5 = layers.Conv2D(32, (3, 3), activation='relu', padding='same')(u5)
    
    outputs = layers.Conv2D(1, (1, 1), activation='sigmoid')(c5)
    return models.Model(inputs, outputs)

model = unet()
model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])

image_datagen = tf.keras.preprocessing.image.ImageDataGenerator(**data_gen_args)
mask_datagen = tf.keras.preprocessing.image.ImageDataGenerator(**data_gen_args)

seed = 42

image_generator = image_datagen.flow(X_train, batch_size=BATCH_SIZE, seed=seed)
mask_generator = mask_datagen.flow(y_train, batch_size=BATCH_SIZE, seed=seed)

def train_generator_fn():
    for img, mask in zip(image_generator, mask_generator):
        yield img, mask


train_dataset = tf.data.Dataset.from_generator(
    train_generator_fn,
    output_signature=(
        tf.TensorSpec(shape=(None, 256, 256, 3), dtype=tf.float32),
        tf.TensorSpec(shape=(None, 256, 256, 1), dtype=tf.float32)
    )
)

early_stop = tf.keras.callbacks.EarlyStopping(
    monitor='val_loss', 
    patience=5, 
    restore_best_weights=True,
    verbose=1
)

fire_pixels = np.sum(y_train)
total_pixels = y_train.size
class_weight = {0: fire_pixels / total_pixels, 1: 1.0}

history = model.fit(
    train_dataset,
    steps_per_epoch=len(X_train) // BATCH_SIZE,
    epochs=EPOCHS,
    validation_data=(X_test, y_test),
    callbacks=[early_stop],
    class_weight=class_weight,
    verbose=1
)

model.save('model_class_weighted.h5')
