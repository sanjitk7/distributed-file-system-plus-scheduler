from models.alexnet import alexnet_predict
from models.resnet import resnet_predict
from PIL import Image

import random
import time

class Executor:
    def __init__(self) -> None:
        pass

    def run_model_1(self, file_path):
        path = "./sdfs/model_1/" + file_path
        img = Image.open(path)
        return alexnet_predict(img, path)

    def run_model_2(self, file_path):
        path = "./sdfs/model_2/" + file_path
        img = Image.open(path)
        return resnet_predict(img, path)

