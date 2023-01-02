from PIL import Image
from torchvision import models, transforms
import torch

def alexnet_predict(img,query_id):
    alexnet = models.alexnet(pretrained=True)
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
    alexnet.eval()  
    out = alexnet(batch_t)
    
    # import list of classes
    with open('./models/imagenet_classes.txt') as f:
        labels = [line.strip() for line in f.readlines()]
        
    # find highest confidence class form output vector
    _, index = torch.max(out, 1) 
    percentage = torch.nn.functional.softmax(out, dim=1)[0] * 100
    
    print("PREDICTED CLASS FOR ",query_id," IS: ",labels[index[0]])  # write into SDFS output file for model 1
    return labels[index[0]]
    # Other High Confidence Labels
    # print(labels[index[0]], percentage[index[0]].item())

    # _, indices = torch.sort(out, descending=True)
    # print([(labels[idx], percentage[idx].item()) for idx in indices[0][:5]])
    

    
    
    
    