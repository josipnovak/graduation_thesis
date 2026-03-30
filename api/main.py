import cv2
import numpy as np
import tensorflow as tf
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import Response
import uvicorn
import io

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
        normalized_img = resized_img.astype("float32") / 255.0
        input_tensor = np.expand_dims(normalized_img, axis=0)

        prediction = model.predict(input_tensor)[0]

        mask = (prediction > 0.5).astype(np.uint8) * 255
        
        mask_final = cv2.resize(mask, (w, h))

        _, buffer = cv2.imencode(".png", mask_final)
        
        return Response(content=buffer.tobytes(), media_type="image/png")

    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":  
    uvicorn.run(app, host="0.0.0.0", port=8000)