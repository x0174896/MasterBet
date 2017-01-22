/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jdk.webservicerest.domain;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author alessio
 */
public class Team implements Serializable{
    
    private Set<Player> players;
    
    private int id;
    
    private String name_team;

    public Team(String team){
        players=new HashSet<Player>();
        name_team=team;
    }
    public Team(){
        players=new HashSet<Player>();
    }
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName_team() {
        return name_team;
    }

    public void setName_team(String name_team) {
        this.name_team = name_team;
    }
    
    public Set<Player> getPlayers() {
        return players;
    }
    
    public void setPlayers(Set<Player> list) {
        players=list;
    }
}
