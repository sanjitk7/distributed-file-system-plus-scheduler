package com.mp3.sdfs;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;  
import javax.xml.bind.annotation.XmlAccessorType;  
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="file")
public class SdfsFile {

    // id = fileName + versionNumber
    private String id;

    private String localFileName;

    private String sdfsFileName;

    private int version;

    private List<String> storedIn;

    SdfsFile () {}
    
    SdfsFile(String id, String localFileName, String sdfsFileName, int version, List<String> storedIn) {
        this.id = id;
        this.localFileName = localFileName;
        this.sdfsFileName = sdfsFileName;
        this.version = version;
        this.storedIn = storedIn;
    }

    public String getId() { return id; }

    public String getLocalFileName() { return localFileName; }

    public String getSdfsFileName() { return sdfsFileName; }

    public int getVersion() { return version; }

    public List<String> getStoredIn() { return storedIn; }

    public void setId(String id) { this.id = new String(id); }

    public void setLocalFileName(String localFileName) { this.localFileName = new String(localFileName); }

    public void setSdfsFileName(String sdfsFileName) { this.sdfsFileName = new String(sdfsFileName); }

    public void setVersion(int version) { this.version = version; }

    public void setStoredIn(List<String> storedIn) { this.storedIn = storedIn; }

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
