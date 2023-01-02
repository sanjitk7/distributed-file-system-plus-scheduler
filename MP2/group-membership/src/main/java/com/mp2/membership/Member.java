package com.mp2.membership;

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

    // IP address in the form IP:<port name>
    private String address;

    private long lastUpdated;

    public Member() {}
    
    public Member(String id, String address, long lastUpdated) {
        this.id = id;
        this.address = address;
        this.lastUpdated = lastUpdated;
    }

    public String getId() { return id; }

    public String getAddress() { return address; }

    public long getLastUpdatedTime() { return lastUpdated; }
    
    public void setId(String id) { this.id = new String(id); }

    public void setLastUpdatedTime(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public void printEntry() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");    
        Date date = new Date(lastUpdated);
        if (this.lastUpdated != 0) {
            System.out.println("ID: " + id + "\t Last updated: " + dateFormat.format(date));
        } else {
            System.out.println("ID: " + id);
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
