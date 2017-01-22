/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jdk.webservicerest.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;

/**
 *
 * @author michele
 */
public class Player implements Serializable {
     
    private int id;
    
    private String name;
    
    private String birthPlace;
    
    private Date birthDate;
    
    private String role;
    
    private int number;
    
    private Set<Team> team;
    
    private boolean purchaseble;

    public Set<Team> getTeam() {
        return team;
    }

    public void setTeam(Set<Team> team) {
        this.team = team;
    }

    
    
    public Player(){}
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public void setBirthPlace(String birthPlace) {
        this.birthPlace = birthPlace;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
    @Override
    public String toString(){
        return name+"@"+birthPlace+"@"+birthDate+"@"+role+"@"+number;
    }

    public Date getBirthDate() {
            return birthDate;
    }
    public void setBirthDate(Date birthDate) {
        this.birthDate=birthDate;
    }
    @Override
    public boolean equals(Object obj){
        Player player=(Player)obj;
        if(this.id==player.id)return true;
        else return false;
    }

    public boolean isPurchaseble() {
        return purchaseble;
    }

    public void setPurchaseble(boolean purchaseble) {
        this.purchaseble = purchaseble;
    }
}