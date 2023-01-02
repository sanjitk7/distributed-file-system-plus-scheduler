package com.mp3.sdfs;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "filesystem")
@XmlSeeAlso({SdfsFile.class, FileReplicas.class})
public class FileSystem {
    @XmlElement(name = "files", type = SdfsFile.class)
    private List<SdfsFile> fileSystem = new ArrayList<SdfsFile>();
    
    @XmlElement(name = "replicas", type = SdfsFile.class)
    private List<FileReplicas> replicas = new ArrayList<FileReplicas>();
    
    public FileSystem() {}

    public FileSystem(List<SdfsFile> fileSystem, List<FileReplicas> replicas) {
        this.fileSystem = fileSystem;
        this.replicas = replicas;
    }

    public List<SdfsFile> getFileSystem() {
        return fileSystem;
    }

    public List<FileReplicas> getReplicas() {
        return replicas;
    }

    public void setFileSystem(List<SdfsFile> fileSystem) {
        this.fileSystem = fileSystem;
    }  
    
    public void setReplicas(List<FileReplicas> replicas) {
        this.replicas = replicas;
    }
}
