import cv2
import numpy as np
import tensorflow as tf
from fastapi import FastAPI, File, UploadFile
import uvicorn
import base64
import os

app = FastAPI()

MODEL_PATH = "model.h5"  
IMG_SIZE = (256, 256)                
THRESHOLD = 0.6

if os.path.exists(MODEL_PATH):
    try:
        model = tf.keras.models.load_model(MODEL_PATH, compile=False)
    except Exception as e:
        exit(1)

@app.get("/")
async def root():
    return {"status": "online", "model": MODEL_PATH, "info": "/docs "}

@app.post("/detect")
async def detect_fire(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            return {"error": "Invalid image file."}

        original_h, original_w = img.shape[:2]

        resized_img = cv2.resize(img, IMG_SIZE)
        img_rgb = cv2.cvtColor(resized_img, cv2.COLOR_BGR2RGB)
        normalized_img = img_rgb.astype("float32") / 255.0
        input_tensor = np.expand_dims(normalized_img, axis=0)

        prediction = model.predict(input_tensor)[0]
        
        if len(prediction.shape) == 3:
            prediction = prediction[:, :, 0]

        prediction_resized = cv2.resize(prediction, (original_w, original_h), interpolation=cv2.INTER_LINEAR)
        mask_final = (prediction_resized > THRESHOLD).astype(np.uint8) * 255
        
        segmented = img.copy()
        red_overlay = np.zeros_like(img)
        red_overlay[:] = (0, 255, 0) 
        
        alpha = 0.5 
        mask_bool = mask_final == 255
        segmented[mask_bool] = cv2.addWeighted(img, 1-alpha, red_overlay, alpha, 0)[mask_bool]
        
        _, mask_buffer = cv2.imencode(".png", mask_final)
        mask_base64 = base64.b64encode(mask_buffer).decode()
        
        _, seg_buffer = cv2.imencode(".png", segmented)
        seg_base64 = base64.b64encode(seg_buffer).decode()
        

        return {
            "fire_detected": bool(np.any(mask_bool)),
            "mask": f"data:image/png;base64,{mask_base64}",
            "segmented_image": f"data:image/png;base64,{seg_base64}"
        }

    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)