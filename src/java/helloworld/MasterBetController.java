/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package helloworld;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import jdk.webservicerest.domain.Player;
import jdk.webservicerest.domain.Team;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author michele
 */
@RestController
@RequestMapping("/masterbet")
public class MasterBetController {
       
    public Set<Player> player;
    Player p;
    
    public MasterBetController(){
        System.out.println("Invocazione construttore controller");
        mockPlayer();
    }
    
    public final void mockPlayer(){
        player = new HashSet<Player>();
        
        
        
    }
    @RequestMapping(value="/list",method=RequestMethod.POST)//richiesto submit
    @ResponseStatus(value=HttpStatus.ACCEPTED)
    public Team getList(@RequestParam String url,@RequestParam String teamName){
        Team team=new Team(teamName);
        team.setPlayers(player);
        return team;
    }
    /*
    @RequestMapping("/list")
    public List<Player> getList(){
        return player; //con la libreria json importata stampa in json
    }
    */
    @RequestMapping("/create")
    public HttpStatus create(Player p){
        player.add(p);
        return HttpStatus.OK;
    }
    @RequestMapping(value="/prova",method=RequestMethod.GET)
    @ResponseStatus(value=HttpStatus.ACCEPTED)
    public String prova(Player p){
        return "prova";
    }
}
