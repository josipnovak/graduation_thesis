import cv2
import numpy as np
import tensorflow as tf
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import uvicorn
import io
import base64
    
app = FastAPI()

MODEL_PATH = "fire_model.h5"  

try:
    model = tf.keras.models.load_model(MODEL_PATH, compile=False)
    print("model loaded")
except Exception as e:
    import h5py
    model = tf.keras.models.load_model(MODEL_PATH, custom_objects=None, compile=False)

@app.get("/")
async def root():
    return {"message": "go to /detect or /docs"}

@app.post("/detect")
async def detect_fire(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            return {"error": "wrong format"}

        h, w = img.shape[:2]

        input_size = (256, 256) 
        resized_img = cv2.resize(img, input_size)
        
        # Try BGR to RGB conversion (OpenCV loads as BGR)
        img_rgb = cv2.cvtColor(resized_img, cv2.COLOR_BGR2RGB)
        normalized_img = img_rgb.astype("float32") / 255.0
        input_tensor = np.expand_dims(normalized_img, axis=0)

        prediction = model.predict(input_tensor)[0]
        
        if len(prediction.shape) == 3:
            prediction = prediction[:, :, 0]

        threshold_value = 0.001
        prediction_resized = cv2.resize(prediction, (w, h), interpolation=cv2.INTER_LINEAR)
        
        mask_final = (prediction_resized > threshold_value).astype(np.uint8) * 255
        print(f"Using threshold: {threshold_value}, mask non-zero pixels: {np.count_nonzero(mask_final)}")
        
        segmented = img.copy()
        red_overlay = np.zeros_like(img)
        red_overlay[:, :] = (0, 0, 255)  
        
        mask_3ch = cv2.cvtColor(mask_final, cv2.COLOR_GRAY2BGR)
        segmented = np.where(mask_3ch == 255, cv2.addWeighted(red_overlay, 0.6, segmented, 0.4, 0), segmented)
        
        _, mask_buffer = cv2.imencode(".png", mask_final)
        mask_base64 = base64.b64encode(mask_buffer).decode()
        
        _, seg_buffer = cv2.imencode(".png", segmented)
        seg_base64 = base64.b64encode(seg_buffer).decode()
        
        return {
            "mask": f"data:image/png;base64,{mask_base64}",
            "segmented_image": f"data:image/png;base64,{seg_base64}"
        }

    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":  
    uvicorn.run(app, host="0.0.0.0", port=8000)