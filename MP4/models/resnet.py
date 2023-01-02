from torchvision import models, transforms
import torch
from PIL import Image

def resnet_predict(img,query_id):
    
    transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize(
    mean=[0.485, 0.456, 0.406],
    std=[0.229, 0.224, 0.225]
    )])
    
    # do pre-prediction transformations on image
    img_t = transform(img)
    
    # put model in eval mode and predict the queried image class
    batch_t = torch.unsqueeze(img_t, 0)
    
    resnet = models.resnet101(pretrained=True)
    resnet.eval()
     
    out = resnet(batch_t)
    
    # import list of classes
    with open('./models/imagenet_classes.txt') as f:
        labels = [line.strip() for line in f.readlines()]
        
    # find highest confidence class form output vector
    _, index = torch.max(out, 1) 
    percentage = torch.nn.functional.softmax(out, dim=1)[0] * 100
    
    print("PREDICTED CLASS FOR ",query_id," IS: ",labels[index[0]])  # write into SDFS output file for model 2
    return labels[index[0]]
