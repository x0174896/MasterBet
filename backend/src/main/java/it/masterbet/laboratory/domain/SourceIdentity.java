package it.masterbet.laboratory.domain;

import java.text.Normalizer;
import java.util.*;

/** Corrispondenze esplicite o normalizzazioni ortografiche, mai similarita arbitraria. */
public final class SourceIdentity {
  private SourceIdentity() {}

  public static String normalized(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace("ß", "ss")
        .replace("ø", "o")
        .replace("ł", "l")
        .replace("đ", "d")
        .replace("æ", "ae")
        .replace("œ", "oe")
        .replace("ı", "i")
        .replaceAll("[^a-z0-9]", "");
  }

  private static final Map<String, String> COUNTRIES =
      Map.ofEntries(
          Map.entry("italia", "italy"),
          Map.entry("inghilterra", "england"),
          Map.entry("germania", "germany"),
          Map.entry("francia", "france"),
          Map.entry("spagna", "spain"),
          Map.entry("norvegia", "norway"),
          Map.entry("paesibassi", "netherlands"),
          Map.entry("olanda", "netherlands"),
          Map.entry("portogallo", "portugal"),
          Map.entry("belgio", "belgium"),
          Map.entry("danimarca", "denmark"),
          Map.entry("austria", "austria"),
          Map.entry("cina", "china"),
          Map.entry("brasile", "brazil"),
          Map.entry("scozia", "scotland"),
          Map.entry("svezia", "sweden"),
          Map.entry("svizzera", "switzerland"),
          Map.entry("grecia", "greece"),
          Map.entry("turchia", "turkey"),
          Map.entry("polonia", "poland"),
          Map.entry("repubblicaceca", "czechrepublic"),
          Map.entry("cechia", "czechrepublic"),
          Map.entry("croazia", "croatia"),
          Map.entry("romania", "romania"),
          Map.entry("russia", "russia"),
          Map.entry("ucraina", "ukraine"),
          Map.entry("finlandia", "finland"),
          Map.entry("arabiasaudita", "saudiarabia"),
          Map.entry("coreadelsud", "southkorea"),
          Map.entry("giappone", "japan"),
          Map.entry("statuniti", "usa"),
          Map.entry("statiuniti", "usa"),
          Map.entry("messico", "mexico"),
          Map.entry("egitto", "egypt"),
          Map.entry("sudafrica", "southafrica"),
          Map.entry("marocco", "morocco"),
          Map.entry("emiratiarabiuniti", "unitedarabemirates"),
          Map.entry("israele", "israel"),
          Map.entry("costadavorio", "ivorycoast"),
          Map.entry("czechia", "czechrepublic"),
          Map.entry("southkorea", "southkorea"),
          Map.entry("republicofkorea", "southkorea"),
          Map.entry("turkiye", "turkey"));

  public static String country(String value) {
    String n = normalized(value);
    return COUNTRIES.getOrDefault(n, n);
  }

  private static final Map<String, String> LEAGUES =
      Map.ofEntries(
          Map.entry("laliga", "laliga"),
          Map.entry("laliga2", "laliga2"),
          Map.entry("laliga2division", "laliga2"),
          Map.entry("ligaportugal", "ligaportugal"),
          Map.entry("primeraliga", "ligaportugal"),
          Map.entry("primeiraliga", "ligaportugal"),
          Map.entry("legaprofessionaledicalcio", "ligaprofesional"),
          Map.entry("ligaprofesionalde futbol".replace(" ", ""), "ligaprofesional"),
          Map.entry("ligaprofesional", "ligaprofesional"),
          Map.entry("superliga", "superliga"),
          Map.entry("jupilerproleague", "proleague"),
          Map.entry("firstdivisiona", "proleague"),
          Map.entry("1hnl", "hnl"),
          Map.entry("prvaliga", "hnl"),
          Map.entry("2bundesliga", "2bundesliga"),
          Map.entry("bundesliga2", "2bundesliga"),
          Map.entry("eflchampionship", "championship"),
          Map.entry("eflleagueone", "leagueone"),
          Map.entry("league1", "leagueone"),
          Map.entry("league2", "leaguetwo"),
          Map.entry("eflleaguetwo", "leaguetwo"),
          Map.entry("aleagueuomini", "aleague"),
          Map.entry("aleaguemen", "aleague"),
          Map.entry("aleaguedonne", "aleaguewomen"),
          Map.entry("aleaguefemminile", "aleaguewomen"),
          Map.entry("k1league", "kleague1"),
          Map.entry("kliga1", "kleague1"),
          Map.entry("legapro", "proleague"),
          Map.entry("saudiproleague", "proleague"),
          Map.entry("campeonatobrasileiroseriea", "seriea"),
          Map.entry("brasileiroseriea", "seriea"),
          Map.entry("campeonatobrasileiroserieb", "serieb"),
          Map.entry("brasileiroserieb", "serieb"),
          Map.entry("superlig", "superlig"),
          Map.entry("ligamx", "ligamx"),
          Map.entry("liga1", "ligai"));

  public static String league(String value) {
    String n = normalized(value);
    return LEAGUES.getOrDefault(n, n);
  }

  /** Provider spellings verified against the archived catalogs; country remains mandatory. */
  public static String calcioLeague(LabCompetition c) {
    String alias =
        switch (c.id()) {
          case 9 -> "Championship";
          case 12 -> "League One";
          case 14 -> "League Two";
          case 17 -> "National League";
          case 45 -> "Super League Women";
          case 181 -> "Bundesliga";
          case 229 -> "Parva Liga";
          case 262 -> "1. Liga";
          case 274 -> "1. Division";
          case 372 -> "Premier League";
          case 474 -> "Superliga";
          case 489 -> "1. Liga";
          case 636 -> "Primera LPF";
          case 779 -> "MLS";
          case 806 -> "Premiership";
          case 860 -> "Botola Pro D1";
          case 902 -> "Pro League";
          case 959 -> "Arabian Gulf League";
          case 989 -> "Chinese Super League";
          case 1073 -> "V-League 1";
          case 1631 -> "Brasileiro A1, Women";
          default -> {
            String slug = normalized(c.slug()), country = normalized(c.country());
            yield slug.startsWith(country) ? slug.substring(country.length()) : c.name();
          }
        };
    return league(alias);
  }

  private static final Map<String, String> TEAMS =
      Map.ofEntries(
          Map.entry("internazionale", "inter"),
          Map.entry("intermilan", "inter"),
          Map.entry("intermilano", "inter"),
          Map.entry("acmilan", "milan"),
          Map.entry("asroma", "roma"),
          Map.entry("sscnapoli", "napoli"),
          Map.entry("parissg", "parissaintgermain"),
          Map.entry("psg", "parissaintgermain"),
          Map.entry("bayernmunich", "bayernmunchen"),
          Map.entry("bayernmunchen", "bayernmunchen"),
          Map.entry("bodoe glimt".replace(" ", ""), "bodoglimt"),
          Map.entry("bodoglimt", "bodoglimt"),
          Map.entry("tromsoe", "tromso"),
          Map.entry("lillestroem", "lillestrom"),
          Map.entry("vaalerenga", "valerenga"),
          Map.entry("aalesunds", "aalesund"),
          Map.entry("kfumoslo", "kfum"),
          Map.entry("manutd", "manchesterunited"),
          Map.entry("manunited", "manchesterunited"),
          Map.entry("man city".replace(" ", ""), "manchestercity"),
          Map.entry("tottenhamhotspur", "tottenham"),
          Map.entry("leytonorientlondon", "leytonorient"),
          Map.entry("atleticomadrid", "atleticomadrid"),
          Map.entry("athleticbilbao", "athleticclub"),
          Map.entry("sportinglisbon", "sportingcp"),
          Map.entry("sporting", "sportingcp"));

  public static String team(String value) {
    String n =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\b(fc|fk|bk|sk|afc|cf|ac|as|ssc|if|ik)\\b", "");
    n = normalized(n);
    return TEAMS.getOrDefault(n, n);
  }
}
