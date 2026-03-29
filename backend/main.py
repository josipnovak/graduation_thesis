import cv2
import numpy as np
import tensorflow as tf
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import Response
import uvicorn
import io

app = FastAPI()

# --- KONFIGURACIJA ---
# Provjeri ime svoje datoteke modela!
MODEL_PATH = "fire_model.h5"  # Promijeni ako se tvoj model zove drugačije ili je u drugoj mapi

# Učitavanje modela pri pokretanju servera
try:
    model = tf.keras.models.load_model(MODEL_PATH, compile=False)
    print("✅ SUSTAV: Model učitan s compile=False!")
except Exception as e:
    # Ako i to padne, probaj ovo:
    import h5py
    model = tf.keras.models.load_model(MODEL_PATH, custom_objects=None, compile=False)

@app.get("/")
async def root():
    return {"poruka": "Server za detekciju vatre radi. Idi na /docs za testiranje."}

@app.post("/detect")
async def detect_fire(file: UploadFile = File(...)):
    try:
        # 1. Čitanje bajtova slike koju šalje mobitel
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if img is None:
            return {"error": "Neispravan format slike"}

        h, w = img.shape[:2]

        # 2. Preprocessing (Prilagodi dimenzije 256, 256 onome što tvoj model traži)
        # Većina UNet modela za vatru koristi 256x256
        input_size = (256, 256) 
        resized_img = cv2.resize(img, input_size)
        normalized_img = resized_img.astype("float32") / 255.0
        input_tensor = np.expand_dims(normalized_img, axis=0)

        # 3. Predikcija (Inferencija)
        prediction = model.predict(input_tensor)[0]

        # 4. Postprocessing (Pretvaranje u masku)
        # Ako model vraća vjerojatnost po pikselu, uzimamo prag 0.5
        mask = (prediction > 0.5).astype(np.uint8) * 255
        
        # Vraćamo masku na originalnu veličinu slike s mobitela
        mask_final = cv2.resize(mask, (w, h))

        # 5. Kodiranje rezultata u PNG format
        _, buffer = cv2.imencode(".png", mask_final)
        
        return Response(content=buffer.tobytes(), media_type="image/png")

    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    # Pokrećemo na 0.0.0.0 da bi mobitel mogao pristupiti preko tvoje IP adrese
    uvicorn.run(app, host="0.0.0.0", port=8000)