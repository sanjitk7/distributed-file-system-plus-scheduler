package com.mp3.sdfs;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;  
import javax.xml.bind.annotation.XmlAccessorType;  
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="replica")
public class FileReplicas {

    // id is the host id
    private String id;

    private List<String> fileList;

    FileReplicas() {}
    
    FileReplicas(String id, List<String> fileList) {
        this.id = id;
        this.fileList = fileList;
    }

    public String getId() { return id; }

    public List<String> getFileList() { return fileList; }

    public void setId(String id) { this.id = new String(id); }

    public void setFileList(List<String> fileList) { this.fileList = fileList; }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null) {
            return false;
        }
 
        if (object.getClass() != this.getClass()) {
            return false;
        }
         
        return this.id.equals(((Member) object).getId());
    }
}
