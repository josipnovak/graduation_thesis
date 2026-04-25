from pathlib import Path

import cv2
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models


IMG_SIZE = (256, 256)
DATASET_ROOT = "---" 
MODEL_PATH   = "---"
THRESHOLD    = 0.5


def load_dataset(image_dir, mask_dir=None):
    image_dir = Path(image_dir)
    mask_dir = Path(mask_dir) if mask_dir else None

    if not image_dir.exists():
        raise FileNotFoundError(f"Image directory not found: {image_dir}")

    image_paths = sorted(
        [p for p in image_dir.iterdir() if p.is_file() and p.suffix.lower() in {".jpg", ".jpeg", ".png"}]
    )

    mask_lookup = {}
    if mask_dir and mask_dir.exists():
        for mask_path in mask_dir.iterdir():
            if mask_path.is_file() and mask_path.suffix.lower() in {".jpg", ".jpeg", ".png"}:
                mask_lookup[mask_path.stem] = mask_path

    images = []
    masks  = []
    names  = []

    for image_path in image_paths:
        image = cv2.imread(str(image_path))
        if image is None:
            continue

        image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        image = cv2.resize(image, IMG_SIZE)
        images.append(image.astype(np.float32) / 255.0)
        names.append(image_path.name)

        if mask_dir and mask_dir.exists():
            mask_path = mask_lookup.get(image_path.stem)
            if mask_path is None:
                raise FileNotFoundError(f"Missing mask for image: {image_path.name}")

            mask = cv2.imread(str(mask_path), cv2.IMREAD_GRAYSCALE)
            if mask is None:
                continue
            mask = cv2.resize(mask, IMG_SIZE)
            mask = (mask > 0).astype(np.float32)
            masks.append(np.expand_dims(mask, axis=-1))

    images = np.asarray(images, dtype=np.float32)
    names  = np.asarray(names)

    if mask_dir and mask_dir.exists():
        masks = np.asarray(masks, dtype=np.float32)
        if len(images) != len(masks):
            raise ValueError("Number of images and masks does not match after loading.")
        return images, masks, names

    return images, None, names

def dice_score(y_true, y_pred):
    y_true = y_true.astype(np.float32).reshape(-1)
    y_pred = y_pred.astype(np.float32).reshape(-1)
    intersection = np.sum(y_true * y_pred)
    return (2.0 * intersection + 1e-7) / (np.sum(y_true) + np.sum(y_pred) + 1e-7)


def iou_score(y_true, y_pred):
    y_true = y_true.astype(np.float32).reshape(-1)
    y_pred = y_pred.astype(np.float32).reshape(-1)
    intersection = np.sum(y_true * y_pred)
    union = np.sum(y_true) + np.sum(y_pred) - intersection
    return (intersection + 1e-7) / (union + 1e-7)


def pixel_accuracy(y_true, y_pred):
    return float(np.mean(y_true == y_pred))


def precision_score(y_true, y_pred):
    y_true = y_true.astype(np.float32).reshape(-1)
    y_pred = y_pred.astype(np.float32).reshape(-1)
    tp = np.sum((y_true == 1) & (y_pred == 1))
    fp = np.sum((y_true == 0) & (y_pred == 1))
    return float((tp + 1e-7) / (tp + fp + 1e-7))


def recall_score(y_true, y_pred):
    y_true = y_true.astype(np.float32).reshape(-1)
    y_pred = y_pred.astype(np.float32).reshape(-1)
    tp = np.sum((y_true == 1) & (y_pred == 1))
    fn = np.sum((y_true == 1) & (y_pred == 0))
    return float((tp + 1e-7) / (tp + fn + 1e-7))

def unet():
    inputs = layers.Input((256, 256, 3))

    c1 = layers.Conv2D(32, (3, 3), padding='same')(inputs)
    c1 = layers.BatchNormalization()(c1)
    c1 = layers.Activation('relu')(c1)
    p1 = layers.MaxPooling2D((2, 2))(c1)

    c2 = layers.Conv2D(64, (3, 3), padding='same')(p1)
    c2 = layers.BatchNormalization()(c2)
    c2 = layers.Activation('relu')(c2)
    p2 = layers.MaxPooling2D((2, 2))(c2)

    c3 = layers.Conv2D(128, (3, 3), padding='same')(p2)
    c3 = layers.BatchNormalization()(c3)
    c3 = layers.Activation('relu')(c3)
    c3 = layers.Dropout(0.3)(c3)

    u4 = layers.Conv2DTranspose(64, (2, 2), strides=(2, 2), padding='same')(c3)
    u4 = layers.concatenate([u4, c2])
    c4 = layers.Conv2D(64, (3, 3), padding='same')(u4)
    c4 = layers.BatchNormalization()(c4)
    c4 = layers.Activation('relu')(c4)

    u5 = layers.Conv2DTranspose(32, (2, 2), strides=(2, 2), padding='same')(c4)
    u5 = layers.concatenate([u5, c1])
    c5 = layers.Conv2D(32, (3, 3), padding='same')(u5)
    c5 = layers.BatchNormalization()(c5)
    c5 = layers.Activation('relu')(c5)

    outputs = layers.Conv2D(1, (1, 1), activation='sigmoid')(c5)
    return models.Model(inputs, outputs)

def load_model(model_path):
    if not Path(model_path).exists():
        raise FileNotFoundError(f"Model file not found: {model_path}")
    return tf.keras.models.load_model(model_path, compile=False)

def evaluate_dataset(model, images, masks, threshold=0.5):
    probabilities = model.predict(images, verbose=0)
    predictions   = (probabilities >= threshold).astype(np.float32)

    per_image_rows = []
    for index in range(len(images)):
        y_true = masks[index]
        y_pred = predictions[index]
        per_image_rows.append(
            {
                "index":                 index,
                "pixel_accuracy":        pixel_accuracy(y_true, y_pred),
                "iou":                   iou_score(y_true, y_pred),
                "dice":                  dice_score(y_true, y_pred),
                "precision":             precision_score(y_true, y_pred),
                "recall":                recall_score(y_true, y_pred),
                "predicted_fire_pixels": int(np.sum(y_pred)),
                "actual_fire_pixels":    int(np.sum(y_true)),
            }
        )

    summary = {
        "pixel_accuracy": float(np.mean([r["pixel_accuracy"] for r in per_image_rows])),
        "iou":            float(np.mean([r["iou"]            for r in per_image_rows])),
        "dice":           float(np.mean([r["dice"]           for r in per_image_rows])),
        "precision":      float(np.mean([r["precision"]      for r in per_image_rows])),
        "recall":         float(np.mean([r["recall"]         for r in per_image_rows])),
    }

    return probabilities, predictions, per_image_rows, summary


def main():
    dataset_root = Path(DATASET_ROOT)
    model = load_model(MODEL_PATH)
    images, masks, names = load_dataset(
        dataset_root / "images_prepped_test",
        dataset_root / "annotations_prepped_test",
    )

    if len(images) == 0:
        raise RuntimeError("No test images were loaded.")

    probabilities, predictions, rows, summary = evaluate_dataset(model, images, masks, threshold=THRESHOLD)

    print(f"Images evaluated: {len(images)}")
    print(f"Pixel accuracy:   {summary['pixel_accuracy']:.4f}")
    print(f"IoU:              {summary['iou']:.4f}")
    print(f"Dice:             {summary['dice']:.4f}")
    print(f"Precision:        {summary['precision']:.4f}")
    print(f"Recall:           {summary['recall']:.4f}")


if __name__ == "__main__":
    main()