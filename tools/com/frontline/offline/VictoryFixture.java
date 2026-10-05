package com.frontline.offline;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// Test-only saves for native victory, migration, and AI checks; not packaged into the app.
public final class VictoryFixture {
    public static void main(String[] args) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(Base64.getDecoder().decode(args[0])));
        NodeList strings = document.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element entry = (Element) strings.item(i);
            if (!entry.getAttribute("name").equals("battle")) continue;
            GameModel model = GameModel.restore(Base64.getDecoder().decode(entry.getTextContent()));
            String mode = args.length > 2 ? args[2] : "--victory";
            if (mode.equals("--v4")) {
                model = new GameModel(12,1,7); model.launch(0,1,.25); model.update(.1f);
                setting(document,"int","selected-sector","12"); setting(document,"int","difficulty","1");
                remove(document,"music");
            } else if (mode.equals("--intercept")) {
                model = new GameModel(0,1,7);
                GameModel.Troop a = new GameModel.Troop(0,5,0,10,4.7f), b = new GameModel.Troop(5,0,1,10,4.7f);
                a.units = 20; b.units = 30; model.troops.add(a); model.troops.add(b);
                setting(document,"int","selected-sector","0");
            } else if (mode.equals("--king")) {
                model = new GameModel(2,1,7);
                model.territories.get(model.territories.size()-1).troops = 0;
                model.troops.add(new GameModel.Troop(0,model.territories.size()-1,0,1,.8f));
                setting(document,"int","selected-sector","2");
            } else if (mode.equals("--overflow")) {
                model = new GameModel(0,1,7); model.territories.get(0).troops = 125;
                model.territories.get(1).owner = 0; model.territories.get(1).troops = 100;
                setting(document,"int","selected-sector","0");
            } else if (mode.equals("--four-kings") || mode.equals("--five-kings")) {
                model = new GameModel(36,1,7); int held = 0;
                for (GameModel.Territory tile : model.territories) {
                    if (tile.capital && tile.owner > 0 && held < (mode.equals("--four-kings") ? 4 : 5)) { tile.owner = 0; held++; }
                }
                model.territories.get(1).owner = 5;
                setting(document,"int","selected-sector","36");
            } else if (mode.equals("--resign")) {
                model = new GameModel(1,1,7);
                for (GameModel.Territory tile : model.territories) { tile.owner = 0; tile.troops = GameModel.troopCap(tile); }
                model.territories.get(model.territories.size()-1).owner = 1;
                model.territories.get(model.territories.size()-1).troops = 1;
                java.lang.reflect.Field timer = GameModel.class.getDeclaredField("dominanceSeconds"); timer.setAccessible(true); timer.setFloat(model,9.5f);
                setting(document,"int","selected-sector","1");
            } else if (mode.equals("--large-map")) {
                model = new GameModel(59,1,7);
                setting(document,"int","selected-sector","59");
                setting(document,"int","unlocked","59");
            } else if (mode.equals("--v5")) {
                for (int sector = 30; sector < GameModel.LEVELS.length; sector++) {
                    remove(document,"best-"+sector); remove(document,"stars-"+sector); remove(document,"time-"+sector);
                }
                model = new GameModel(29,1,7); model.elapsed = 42; model.unitsSent = 20;
                model.territories.get(0).troops = 2500; model.territories.get(1).troops = 500;
                GameModel.Troop convoy = new GameModel.Troop(0,1,0,4,.25f); convoy.units = 1100; model.troops.add(convoy);
                setting(document,"int","best-29","2700"); setting(document,"int","stars-29","3"); setting(document,"float","time-29","90");
                setting(document,"int","unlocked","29"); setting(document,"int","selected-sector","29");
                setting(document,"int","difficulty","1"); setting(document,"int","wins","30");
                setting(document,"boolean","music","false"); setting(document,"boolean","tutorial-seen","true");
            } else if (mode.equals("--legacy")) {
                model = new GameModel(5,1,7); model.outcome = GameModel.WON;
                for (GameModel.Territory territory : model.territories) if (territory.owner > 0) territory.owner = 0;
                NodeList entries = document.getDocumentElement().getChildNodes();
                for (int j = entries.getLength()-1; j >= 0; j--) {
                    if (!(entries.item(j) instanceof Element)) continue;
                    Element setting = (Element) entries.item(j);
                    String key = setting.getAttribute("name");
                    if (key.startsWith("best-") || key.startsWith("stars-") || key.startsWith("time-"))
                        document.getDocumentElement().removeChild(setting);
                }
                for (int level = 0; level < 6; level++) {
                    setting(document,"int","best-"+level,Integer.toString(1800+level*100));
                    setting(document,"int","stars-"+level,"3");
                    setting(document,"float","time-"+level,Integer.toString(50+level*10));
                }
                setting(document,"int","unlocked","5"); setting(document,"int","selected-sector","5");
                setting(document,"int","wins","6"); setting(document,"boolean","tutorial-seen","true");
                remove(document,"music");
            } else if (mode.equals("--campaign")) {
                model = new GameModel(12,1,7);
                setting(document,"int","unlocked","59"); setting(document,"int","selected-sector","12");
                setting(document,"int","difficulty","1"); setting(document,"boolean","tutorial-seen","true");
            } else if (mode.equals("--ai")) {
                model = new GameModel(0,1,7);
                for (GameModel.Territory territory : model.territories) { territory.owner = -1; territory.troops = 99; }
                model.territories.get(0).owner = 0;
                model.territories.get(model.territories.size()-1).owner = 1;
                model.territories.get(model.territories.size()-1).troops = 80;
                model.territories.get(model.territories.size()-2).troops = 8;
                setting(document,"int","difficulty","1");
            } else {
                for (GameModel.Territory territory : model.territories) if (territory.owner > 0) territory.owner = 0;
                model.troops.clear(); model.outcome = GameModel.PLAYING;
            }
            byte[] battle = mode.equals("--v5") ? LegacySave.encode(model,true) : mode.equals("--legacy") || mode.equals("--v4") ? LegacySave.encode(model,false) : model.save();
            entry.setTextContent(Base64.getEncoder().encodeToString(battle));
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document),new StreamResult(new File(args[1])));
            return;
        }
        throw new IllegalArgumentException("Fixture requires a saved battle");
    }

    private static void remove(Document document,String name) {
        NodeList entries = document.getDocumentElement().getChildNodes();
        for (int i = entries.getLength()-1; i >= 0; i--)
            if (entries.item(i) instanceof Element && ((Element)entries.item(i)).getAttribute("name").equals(name))
                document.getDocumentElement().removeChild(entries.item(i));
    }

    private static void setting(Document document,String type,String name,String value) {
        NodeList entries = document.getElementsByTagName(type);
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            if (entry.getAttribute("name").equals(name)) { entry.setAttribute("value",value); return; }
        }
        Element entry = document.createElement(type); entry.setAttribute("name",name); entry.setAttribute("value",value);
        document.getDocumentElement().appendChild(entry);
    }
}
