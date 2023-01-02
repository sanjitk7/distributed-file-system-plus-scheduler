package com.mp2.introducer;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "members")
public class Members {
    @XmlElement(name = "member", type = Member.class)
    private List<Member> membershipList = new ArrayList<Member>();
    
    public Members() {}

    public Members(List<Member> membershipList) {
        this.membershipList = membershipList;
    }

    public List<Member> getMembers() {
        return membershipList;
    }

    public void setMembers(List<Member> membershipList) {
        this.membershipList = membershipList;
    }   
}
