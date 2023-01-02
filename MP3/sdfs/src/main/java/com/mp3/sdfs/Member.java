package com.mp3.sdfs;

import javax.xml.bind.annotation.XmlRootElement;

import java.sql.Date;
import java.text.SimpleDateFormat;

import javax.xml.bind.annotation.XmlAccessType;  
import javax.xml.bind.annotation.XmlAccessorType;  

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="member")
public class Member {

    // ID is in the format <IP address>:<port number>_<creation timestamp>
    private String id;

    // Host name is easier to read and identify
    private String hostName;

    // IP address in the form IP:<port name>
    private String address;

    private long lastUpdated;

    private boolean isLeader;

    public Member() {}
    
    public Member(String id, String address, String hostName, long lastUpdated, boolean isLeader) {
        this.id = id;
        this.address = address;
        this.hostName = hostName;
        this.lastUpdated = lastUpdated;
        this.isLeader = isLeader;
    }

    public String getId() { return id; }

    public String getAddress() { return address; }

    public String getHostName() { return hostName; }

    public long getLastUpdatedTime() { return lastUpdated; }

    public boolean getIsLeader() { return isLeader; }
    
    public void setId(String id) { this.id = new String(id); }

    public void setIsLeader(boolean isLeader) { this.isLeader = isLeader; }

    public void setLastUpdatedTime(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public void printEntry() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");    
        Date date = new Date(lastUpdated);
        
        if (this.lastUpdated != 0) {
            System.out.println(hostName + "\t\t Last updated: " + dateFormat.format(date) + "\t\t isLeader: " + isLeader);
        
        } else {
            System.out.println(hostName + "\t\t isLeader: " + isLeader);
        }
    }

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
