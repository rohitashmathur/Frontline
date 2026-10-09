package com.frontline.offline;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class V11LogisticsModelTest {
    private static int checks;
    public static void main(String[] args) throws Exception { System.out.println("PASS: "+run()+" focused logistics model checks."); }
    public static int run() throws Exception {
        checks = 0;
        configuration(); legality(); transitAndCaps(); interruption(); delays(); interception(); arrivalOrder();
        predictions(); persistence(); invalidSaves(); legacyAndClassic(); actualAi(); matchedDurationSample();
        return checks;
    }

    private static GameModel.Level grid() {
        return new GameModel.Level("Routing fixture",3,3,new int[] {},100,new int[] {0,8},new int[] {1},32,32,7);
    }

    private static GameModel routed(GameModel.Level level) throws Exception {
        GameModel model = new GameModel(0,1,73,level); model.configureLogistics(0); quiet(model); return model;
    }

    private static GameModel chain() throws Exception {
        GameModel model = routed(grid()); model.territories.get(1).owner = 0; model.territories.get(4).owner = 0;
        model.territories.get(0).troops = 125; return model;
    }

    private static void configuration() throws Exception {
        for (int id = 0; id < Logistics.PRESETS.length; id++) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = new GameModel(0,difficulty,17,Logistics.PRESETS[id].level); model.configureLogistics(id,1);
            check(model.battleMode == 2 && model.logisticsId == id && model.logisticsConfigVersion == 1
                && model.routingVersion == 1 && model.missionConfigVersion == 0,"Versioned stable logistics association");
            check(ByteBuffer.wrap(model.save()).getInt() == 0x464C3036,"Configured logistics uses FL06");
            exact(model);
            for (int owner = 0; owner <= model.level().opponents; owner++) check(model.owned(owner) == 1
                && model.army(owner) == model.level().playerTroops,"Logistics keeps equal entire starting armies");
            illegal(() -> model.configureLogistics(idFor(model)),true);
            model.outcome = GameModel.WON; model.terminalReason = GameModel.TERMINAL_VICTORY;
            check(model.score() == 0 && model.stars() == 0,"Logistics awards no campaign score/stars");
        }
        GameModel model = new GameModel(0,1,73,grid()); illegal(() -> model.configureLogistics(-1),false);
        illegal(() -> model.configureLogistics(3),false); illegal(() -> model.configureLogistics(0,2),false);
        model.update(.05f); illegal(() -> model.configureLogistics(0),true);
        GameModel run = new GameModel(0,1,73,grid()); run.configureRun(0,"run",0); illegal(() -> run.configureLogistics(0),true);
        illegal(() -> run.labAi(new int[] {0,1},false),true);
        GameModel experiment = Logistics.create(0,1,17);
        GameModel.LabAi lab = experiment.labAi(new int[] {GameModel.PRESSURE,GameModel.GUARDIAN},false);
        lab.step(.05f);
        check(experiment.outcome == GameModel.PLAYING && experiment.routingVersion == 1,
            "Lab accepts routed experiments without changing player outcome semantics");
    }

    private static int idFor(GameModel model) { return model.logisticsId; }

    private static void legality() throws Exception {
        GameModel model = routed(grid()); byte[] initial = model.save();
        check(model.route(0,8,0) == null && Float.isInfinite(model.routeEta(0,8,0)),"No attack across a neutral interior");
        check(model.previewAmount(0,8,1) == 0 && model.launch(0,8,1) == 0,"Impossible route cannot spend troops");
        for (int[] action : new int[][] {{-1,1},{0,-1},{0,99},{0,0},{8,0}})
            if (action[0] != 8) check(model.previewAmount(action[0],action[1],.5) == 0 && model.launch(action[0],action[1],.5) == 0,"Invalid action refuses");
        check(Arrays.equals(initial,model.save()),"Rejected actions/previews change no accounting or random state");
        check(model.route(0,1,1) == null && model.route(0,1,-1) == null,"Route dispatch requires correct source owner");
        model.territories.get(4).owner = 0;
        check(model.route(0,4,0) == null,"Disconnected friendly reinforcement is refused");
        model.territories.get(1).owner = 0;
        check(Arrays.equals(model.route(0,4,0),new int[] {0,1,4}),"Friendly reinforcement follows connected shortest route");
        check(Arrays.equals(model.route(0,8,0),new int[] {0,1,4,8}),"Attack has exactly one hostile final hop");
        check(model.routeEta(0,8,0) == LogisticsRoutes.eta(model,0,8,0),"Preview shares route geometry and per-hop delay");
        int[] preview = model.route(0,8,0); preview[1] = 99;
        check(model.route(0,8,0)[1] == 1,"Preview does not expose stored model route state");
        model = routed(new GameModel.Level("Disconnected hole fixture",3,3,new int[] {1,3},100,new int[] {0,8},new int[] {1},32,32,7));
        initial = model.save();
        check(LogisticsRoutes.neighbors(model,0).length == 0 && model.launch(0,model.originalKing(1),1) == 0,"Holes cannot be crossed as long edges");
        check(Arrays.equals(initial,model.save()),"Hole refusal is free");
    }

    private static void transitAndCaps() throws Exception {
        GameModel model = routed(grid()); model.territories.get(3).owner = model.territories.get(7).owner = 0;
        model.territories.get(3).troops = 100; model.territories.get(7).troops = 0; model.territories.get(0).troops = 125;
        fillQueue(model,899);
        check(model.launch(0,7,1) == 125 && model.troops.size() == 900 && model.unitsSent == 125,"One free packet carries all 125 units without clamping");
        GameModel.RouteTroop packet = (GameModel.RouteTroop)model.troops.get(899);
        check(packet.units == 125 && Arrays.equals(packet.route,new int[] {0,3,7}),"Grouped routed packet retains its entire army");
        packet.age = packet.duration-.01f; model.update(.02f);
        check(packet.leg == 1 && packet.source == 3 && packet.target == 7 && packet.units == 125,"Scene endpoints become the current leg");
        check(model.territories.get(3).count() == 100 && model.cappedReinforcements == 0,"Friendly transit never deposits/clips units");
        exact(model);
        model.territories.get(7).troops = 0; packet.age = packet.duration-.01f; model.update(.02f);
        check(!model.troops.contains(packet) && model.territories.get(7).count() == 100 && model.cappedReinforcements == 25,"Only final friendly arrival uses existing cap loss accounting");
        check(eventUnits(model,GameModel.CAP_LOSS_EVENT,7) == 25,"Final cap loss event is factual");
        int sent = model.unitsSent; double before = model.territories.get(0).troops;
        fillQueue(model,1);
        check(model.previewAmount(0,3,1) == 0 && model.launch(0,3,1) == 0 && model.unitsSent == sent
            && model.territories.get(0).troops == before,"Full queue consumes no troops or budget");
    }

    private static void fillQueue(GameModel model, int count) {
        for (int i = 0; i < count; i++) model.troops.add(new GameModel.RouteTroop(new int[] {8,5},1,hop(model,8,5),-20));
    }

    private static void interruption() throws Exception {
        for (int hostile : new int[] {GameModel.NEUTRAL,1}) {
            GameModel model = chain(); model.territories.get(8).troops = 0;
            GameModel.RouteTroop packet = packet(model,new int[] {0,1,4,8},0,20,0);
            model.territories.get(1).owner = hostile; model.territories.get(1).troops = 5;
            model.territories.get(3).owner = 0;
            check(Arrays.equals(model.route(0,8,0),new int[] {0,3,4,8}),"New preview may find an alternative, but a launched route is frozen");
            GameModel restored = GameModel.restore(model.save()); exact(model);
            packet.age = packet.duration-.01f;
            ((GameModel.RouteTroop)restored.troops.get(0)).age = packet.age;
            model.update(.02f); restored.update(.02f);
            check(model.troops.isEmpty() && model.territories.get(1).owner == 0 && model.territories.get(1).count() == 15,"Interrupted whole packet fights once and stops even after capture");
            check(model.territories.get(4).owner == 0 && model.territories.get(8).owner == 1,"Interruption cannot continue to later hops");
            check(model.unitsLost == 5 && model.captures == 1 && model.intercepted == 0,"Ordinary arrival loss/capture accounting at transit");
            check(Arrays.equals(model.save(),restored.save()),"Stale hostile transit save resumes identical combat");
            check(eventUnits(model,GameModel.ROUTE_INTERRUPTED_EVENT,1) == 20,"Interruption event names exact tile and stopped packet amount");
        }
        GameModel model = chain(); GameModel.RouteTroop packet = packet(model,new int[] {0,1,4,8},0,3,0);
        model.territories.get(1).owner = 1; model.territories.get(1).troops = 30;
        packet.age = packet.duration-.01f; model.update(.02f);
        check(model.troops.isEmpty() && model.unitsLost == 3 && model.territories.get(1).owner == 1,"Losing transit combat terminates whole packet too");
        check(eventUnits(model,GameModel.ROUTE_INTERRUPTED_EVENT,1) == 3,"Defeated packet also reports interruption");
        model = chain(); packet = packet(model,new int[] {0,1,4,8},0,20,0);
        model.territories.get(1).owner = 1; model.territories.get(1).owner = 0;
        packet.age = packet.duration-.01f; model.update(.02f);
        check(packet.leg == 1 && model.troops.contains(packet) && eventUnits(model,GameModel.ROUTE_INTERRUPTED_EVENT,1) == 0,"Recovery before arrival permits passage; no invented past failure");
    }

    private static void delays() throws Exception {
        GameModel model = chain(); model.territories.get(0).troops = 3;
        check(model.launch(0,4,1) == 3,"Launch creates staggered routed packets");
        check(model.troops.get(0).age == 0 && model.troops.get(1).age == -.022f && model.troops.get(2).age == -.044f,"Existing startup packet delays retained");
        model.update(.01f);
        check(model.troops.get(0).age > 0 && model.troops.get(1).age < 0 && model.troops.get(2).age < 0,"Delayed packets do not move early");
        float lastFirstArrival = model.troops.get(2).duration+.044f;
        while (model.elapsed < lastFirstArrival+.05f) model.update(.05f);
        for (GameModel.Troop troop : model.troops) check(((GameModel.RouteTroop)troop).leg == 1 && troop.age >= 0,"Stagger is not restarted on each leg");
        check(Math.abs(model.troops.get(0).age-model.troops.get(2).age-.044f) < .000001f,"Startup spacing is carried across hop boundaries");
        exact(model);
    }

    private static void interception() throws Exception {
        GameModel model = routed(grid()); model.territories.get(1).owner = 0; model.territories.get(4).owner = 1;
        GameModel.RouteTroop player = packet(model,new int[] {0,1,4},0,10,0);
        player.age = player.duration-.01f;
        GameModel.RouteTroop enemy = packet(model,new int[] {4,1},1,10,0); enemy.age = enemy.duration*.75f;
        model.update(.1f);
        check(model.troops.isEmpty() && model.intercepted == 10 && model.unitsLost == 10,"Opposing next-leg paths clash within the same update remainder");
        check(eventUnits(model,GameModel.INTERCEPT_EVENT,4) == 10 && !model.clashes.isEmpty(),"Current-leg interception tile and effect remain visible");
        check(model.cappedReinforcements == 0,"Interception cannot produce transit cap loss");
    }

    private static void arrivalOrder() throws Exception {
        for (boolean playerLast : new boolean[] {false,true}) {
            GameModel model = routed(grid()); model.territories.get(2).owner = 1; model.territories.get(1).troops = 0;
            GameModel.RouteTroop a = new GameModel.RouteTroop(new int[] {0,1},0,hop(model,0,1),0);
            GameModel.RouteTroop b = new GameModel.RouteTroop(new int[] {2,1},1,hop(model,2,1),0);
            a.units = b.units = 10; a.age = a.duration-.001f; b.age = b.duration-.001f;
            model.troops.add(playerLast ? b : a); model.troops.add(playerLast ? a : b);
            GameModel restored = GameModel.restore(model.save()); model.update(.02f); restored.update(.02f);
            check(model.territories.get(1).owner == (playerLast ? 0 : 1) && model.territories.get(1).count() == 0,"Exact arrival ties resolve by reverse persisted packet order");
            check(model.unitsLost == 10 && Arrays.equals(model.save(),restored.save()),"Tie loss accounting and replay are deterministic");
        }
        GameModel model = routed(grid()); model.territories.get(2).owner = 1; model.territories.get(1).troops = 0;
        GameModel.RouteTroop earlier = packet(model,new int[] {2,1},1,10,0); earlier.age = earlier.duration-.001f;
        GameModel.RouteTroop later = packet(model,new int[] {0,1},0,10,0); later.age = later.duration-.002f;
        model.update(.02f);
        check(model.territories.get(1).owner == 1,"Different arrival times resolve chronologically, not by list position");
    }

    private static void predictions() throws Exception {
        GameModel model = routed(grid());
        for (GameModel.Territory tile : model.territories) if (!tile.capital) { tile.owner = -1; tile.troops = 99; }
        for (int id : new int[] {2,4,5,7}) { model.territories.get(id).owner = 1; model.territories.get(id).troops = 0; }
        model.territories.get(2).troops = 80; model.territories.get(8).troops = 0;
        model.territories.get(6).owner = 0; model.territories.get(6).troops = 30; model.aiVersion = 1;
        float eta = model.routeEta(2,6,1);
        check(eta > hop(model,2,6),"AI route ETA includes detour and every hop's delay");
        invoke(model,"playAi",new Class<?>[] {int.class},1);
        int amount = 0;
        for (GameModel.Troop troop : model.troops) {
            check(troop instanceof GameModel.RouteTroop && ((GameModel.RouteTroop)troop).destination() == 6,"AI uses the same legal routed launch");
            amount += troop.units;
        }
        int expected = (int)Math.ceil((30+1.35*eta+5)/(1-1.35*.022));
        check(amount == expected,"AI capture budget uses full routed ETA, not direct travel");
        check(number(model,"incoming",new Class<?>[] {int.class,int.class,boolean.class},6,0,false) == amount,"Incoming is credited to final destination, not next leg");
        check(number(model,"incoming",new Class<?>[] {int.class,int.class,boolean.class},4,1,true) == 0,"Pass-through packets are not friendly reinforcements");
        float first = (Float)invoke(model,"firstArrival",new Class<?>[] {int.class,int.class,boolean.class},6,0,false);
        float last = (Float)invoke(model,"lastArrival",new Class<?>[] {int.class,int.class,boolean.class},6,0,false);
        check(Math.abs(first-eta) < .000001f && Math.abs(last-eta-(amount-1)*.022f) < .000002f,"Arrival prediction includes suffix legs and initial delays");
        check(number(model,"incomingBefore",new Class<?>[] {int.class,int.class,boolean.class,float.class},6,0,false,eta-.01f) == 0,"Future final arrivals cannot defend early");
        model.territories.get(4).owner = 0;
        check(number(model,"incoming",new Class<?>[] {int.class,int.class,boolean.class},6,0,false) == 0
            && number(model,"incoming",new Class<?>[] {int.class,int.class,boolean.class},4,0,false) == amount,"Prediction switches to the first hostile transit, not a now-impossible final delivery");
        check((Boolean)invoke(model,"canRecapture",new Class<?>[] {int.class},1),"Guarded resignation counts routed recovery armies");
        exact(model);
    }

    private static void persistence() throws Exception {
        GameModel model = chain(); model.launch(0,8,.5);
        for (int tick = 0; tick < 24; tick++) model.update(.05f);
        check(((GameModel.RouteTroop)model.troops.get(0)).leg > 0,"Save fixture is genuinely mid-route");
        GameModel restored = GameModel.restore(model.save());
        check(restored.logisticsId == model.logisticsId && restored.routingVersion == 1
            && restored.logisticsConfigVersion == 1 && Arrays.equals(model.save(),restored.save()),"FL06 retains route, current leg, delay and stable metadata");
        for (int tick = 0; tick < 300; tick++) {
            model.update(.05f); restored.update(.05f);
            check(Arrays.equals(model.save(),restored.save()),"Mid-route continuation is byte-identical including future AI/RNG");
        }
        Logistics original = Logistics.PRESETS[0]; model = chain(); model.launch(0,8,.25); byte[] saved = model.save();
        try {
            Logistics.PRESETS[0] = Logistics.PRESETS[1]; restored = GameModel.restore(saved);
            check(restored.logisticsId == 0 && restored.level().name.equals("Routing fixture")
                && restored.territories.size() == 9 && Arrays.equals(saved,restored.save()),"Frozen topology/stable ID do not depend on today's catalog name or shape");
        } finally { Logistics.PRESETS[0] = original; }
        model = chain(); fillQueue(model,899); model.launch(0,8,1); exact(model);
        check(model.troops.size() == 900,"Maximum packet queue is persisted without losing grouped units");
    }

    private static void invalidSaves() throws Exception {
        GameModel model = chain(); GameModel.RouteTroop packet = packet(model,new int[] {0,1,4,8},0,20,0);
        byte[] saved = model.save(); int extension = saved.length-(24+packet.route.length*4), length = extension+16;
        for (int offset : new int[] {extension,extension+4,extension+8,extension+12,length,saved.length-4}) {
            byte[] invalid = saved.clone(); ByteBuffer.wrap(invalid).putInt(offset,-1); reject(invalid);
        }
        byte[] invalid = saved.clone(); ByteBuffer.wrap(invalid).putInt(extension+8,2); reject(invalid);
        invalid = saved.clone(); ByteBuffer.wrap(invalid).putInt(length,900); reject(invalid);
        invalid = saved.clone(); ByteBuffer.wrap(invalid).putInt(length+8,0); reject(invalid);
        invalid = saved.clone(); ByteBuffer.wrap(invalid).putInt(length+8,8); reject(invalid);
        reject(Arrays.copyOf(saved,saved.length-1)); reject(Arrays.copyOf(saved,saved.length+1)); reject(new byte[600001]);
        packet.leg = 3; badModel(model); packet.leg = 0;
        packet.source = 1; badModel(model); packet.source = 0;
        packet.duration += .01f; badModel(model); packet.duration = hop(model,0,1);
        packet.age = Float.NaN; badModel(model); packet.age = -31; badModel(model); packet.age = packet.duration; badModel(model); packet.age = 0;
        packet.leg = 1; packet.source = 1; packet.target = 4; packet.duration = hop(model,1,4); packet.age = -.01f; badModel(model);
        packet.age = 0; exact(model);
        model.routingVersion = 2; badModel(model); model.routingVersion = 1;
        model.battleMode = GameModel.MODE_CAMPAIGN; badModel(model); model.battleMode = GameModel.MODE_LOGISTICS;
        model.runPerks = 1; badModel(model); model.runPerks = 0;
        model.logisticsId = 999; badModel(model); model.logisticsId = 0;
        exact(model);
    }

    private static void legacyAndClassic() throws Exception {
        for (int version = 1; version <= 5; version++) {
            GameModel old = new GameModel(0,1,89,version <= 4 ? 10 : GameModel.RULES_VERSION);
            old.launch(0,1,.25); old.update(.05f);
            byte[] saved = oldFormat(old,version); GameModel restored = GameModel.restore(saved);
            check(restored.routingVersion == 0 && restored.logisticsId == -1 && restored.logisticsConfigVersion == 0,"FL01-05 are never guessed into routed rules");
            check(!(restored.troops.get(0) instanceof GameModel.RouteTroop) && restored.troops.get(0).target == 1,"Legacy direct packet retains direct movement");
            exact(restored);
        }
        for (int mode : new int[] {0,1,2}) for (int map : new int[] {0,12,36}) for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameModel model = new GameModel(map,difficulty,991+map); model.aiVersion = 1;
            if (mode == 1) model.configureRun(GameModel.ALL_RUN_PERKS,"frozen-run",3);
            if (mode == 2) model.battleMode = GameModel.MODE_LOGISTICS;
            for (int tick = 0; tick < 40; tick++) model.update(.05f);
            check(Arrays.equals(model.save(),oldFormat(model,5)),"Classic/Run/unconfigured FL05 payload remains byte-for-byte unchanged");
            GameModel restored = GameModel.restore(oldFormat(model,5));
            for (int tick = 0; tick < 150; tick++) {
                if (tick%31 == 0) {
                    int source = model.originalKing(0), target = (tick/31+1)%model.territories.size();
                    check(model.launch(source,target,.5) == restored.launch(source,target,.5),"Old direct launch amounts remain identical");
                }
                model.update(.05f); restored.update(.05f);
                check(Arrays.equals(model.save(),restored.save()),"FL05 Classic/Run/legacy mode continuation remains byte-identical");
            }
        }
    }

    private static void actualAi() throws Exception {
        for (int id = 0; id < Logistics.PRESETS.length; id++) for (int difficulty = 0; difficulty < 3; difficulty++) for (int seed : new int[] {0,7,1000}) {
            GameModel model = new GameModel(0,difficulty,seed,Logistics.PRESETS[id].level); model.configureLogistics(id); model.aiVersion = 1;
            Set<GameModel.Troop> previous = new HashSet<>();
            for (int tick = 0; tick < 400 && model.outcome == GameModel.PLAYING; tick++) {
                if (tick%8 == 0) command(model);
                model.update(.05f);
                check(model.troops.size() <= 900,"Actual routed games preserve packet limit");
                for (GameModel.Troop troop : model.troops) {
                    check(troop instanceof GameModel.RouteTroop,"Every real player/AI packet uses routes");
                    GameModel.RouteTroop packet = (GameModel.RouteTroop)troop;
                    check(packet.units > 0 && packet.source == packet.route[packet.leg] && packet.target == packet.route[packet.leg+1],"Current leg remains consistent after actual simulation");
                    if (!previous.contains(packet) && packet.owner != 0) {
                        check(packet.leg == 0 && Arrays.equals(packet.route,model.route(packet.route[0],packet.destination(),packet.owner)),"AI's new route is the same legal deterministic preview");
                    }
                }
                if (tick%20 == 0) {
                    GameModel restored = GameModel.restore(model.save()); check(Arrays.equals(model.save(),restored.save()),"Actual multi-faction simulation snapshots retain routes");
                    model.update(.01f); restored.update(.01f); check(Arrays.equals(model.save(),restored.save()),"Resumed multi-faction AI has exact next state");
                }
                previous = new HashSet<>(model.troops); model.drainEvents();
            }
        }
    }

    private static void matchedDurationSample() throws Exception {
        System.out.println("Matched automated sample: same frozen map/Normal/seeds 0-4/controller; step=0.05s; cap=120s. Human validation pending.");
        for (int id = 0; id < Logistics.PRESETS.length; id++) for (boolean routed : new boolean[] {false,true}) {
            int wins = 0, losses = 0, timeouts = 0; double seconds = 0;
            for (int seed = 0; seed < 5; seed++) {
                GameModel model = new GameModel(0,1,seed,Logistics.PRESETS[id].level); model.aiVersion = 1;
                if (routed) model.configureLogistics(id);
                for (int tick = 0; tick < 2400 && model.outcome == GameModel.PLAYING; tick++) { if (tick%8 == 0) command(model); model.update(.05f); model.drainEvents(); }
                if (model.outcome == GameModel.WON) wins++; else if (model.outcome == GameModel.LOST) losses++; else timeouts++;
                seconds += model.elapsed;
            }
            System.out.printf(java.util.Locale.US,"map=%d mode=%s wins=%d/5 losses=%d/5 timeouts=%d/5 mean_elapsed=%.3fs%n",id,routed ? "routed" : "Classic",wins,losses,timeouts,seconds/5);
        }
    }

    private static void command(GameModel model) {
        for (GameModel.Territory source : model.territories) if (source.owner == 0 && source.count() >= 20) {
            GameModel.Territory best = null; float score = Float.MAX_VALUE; int amount = 0;
            for (GameModel.Territory target : model.territories) if (target.owner != 0) {
                float eta = model.routeEta(source.id,target.id,0); if (!Float.isFinite(eta)) continue;
                double rate = target.owner == -1 ? 0 : (target.capital ? 2.2 : 1.35)*model.teamMultiplier(target.owner);
                int required = (int)Math.ceil((Math.min(model.capacity(target),target.troops+rate*eta)+7)/(1-rate*.022));
                float preference = required+eta*3-(target.capital ? 12 : 0);
                if (required <= source.count()-12 && preference < score) { best = target; score = preference; amount = required; }
            }
            if (best != null) model.launch(source.id,best.id,(amount+.00001)/source.count());
        }
    }

    private static GameModel.RouteTroop packet(GameModel model, int[] route, int owner, int units, float age) {
        GameModel.RouteTroop packet = new GameModel.RouteTroop(route,owner,hop(model,route[0],route[1]),age); packet.units = units; model.troops.add(packet); return packet;
    }
    private static float hop(GameModel model, int a, int b) {
        GameModel.Territory x = model.territories.get(a), y = model.territories.get(b); return .3f+(float)Math.hypot(x.x-y.x,x.y-y.y)/2.6f;
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void quiet(GameModel model) throws Exception { Arrays.fill((float[])field(GameModel.class,"aiTimers").get(model),10); }
    private static Object invoke(GameModel model, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = GameModel.class.getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(model,args);
    }
    private static int number(GameModel model, String name, Class<?>[] types, Object... args) throws Exception { return (Integer)invoke(model,name,types,args); }
    private static int eventUnits(GameModel model, int type, int tile) { int units = 0; for (GameModel.BattleEvent event : model.drainEvents()) if (event.type == type && event.tile == tile) units += event.units; return units; }
    private static void exact(GameModel model) throws Exception { check(Arrays.equals(model.save(),GameModel.restore(model.save()).save()),"Exact battle save roundtrip"); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void reject(byte[] bytes) throws Exception { try { GameModel.restore(bytes); throw new AssertionError("Invalid save accepted"); } catch (IOException expected) { checks++; } }
    private static void badModel(GameModel model) throws Exception { try { GameModel.restore(model.save()); throw new AssertionError("Invalid route model accepted"); } catch (IOException expected) { checks++; } }
    private static void illegal(Runnable action, boolean state) { try { action.run(); throw new AssertionError("Invalid configuration accepted"); } catch (IllegalArgumentException expected) { check(!state,"Configuration argument rejected"); } catch (IllegalStateException expected) { check(state,"Configuration state rejected"); } }

    private static byte[] oldFormat(GameModel model, int version) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x464C3030+version); out.writeInt(model.levelIndex); out.writeInt(model.difficulty);
        out.writeFloat(model.elapsed); out.writeInt(model.outcome); out.writeInt(model.captures); out.writeInt(model.unitsLost); out.writeInt(model.unitsSent);
        if (version == 5) {
            GameModel.Level level = model.level(); out.writeUTF(level.name); out.writeInt(level.columns); out.writeInt(level.rows); out.writeInt(level.opponents); out.writeInt(level.parSeconds);
            ints(out,level.holes); ints(out,level.factions); out.writeBoolean(level.startingCells != null); if (level.startingCells != null) ints(out,level.startingCells);
            out.writeInt(level.playerTroops); out.writeInt(level.rivalTroops); out.writeInt(level.neutralMinimum);
        }
        out.writeInt(model.territories.size()); for (GameModel.Territory territory : model.territories) { out.writeInt(territory.owner); out.writeDouble(territory.troops); }
        out.writeInt(model.troops.size());
        for (GameModel.Troop troop : model.troops) { out.writeInt(troop.source); out.writeInt(troop.target); out.writeInt(troop.owner); out.writeFloat(troop.duration); out.writeFloat(troop.age); if (version >= 2) out.writeInt(troop.units); }
        float[] timers = (float[])field(GameModel.class,"aiTimers").get(model); for (int i = 0; i < (version >= 3 ? 6 : 4); i++) out.writeFloat(timers[i]);
        if (version >= 3) { out.writeFloat(field(GameModel.class,"dominanceSeconds").getFloat(model)); for (boolean resigned : model.resigned) out.writeBoolean(resigned); }
        if (version >= 4) {
            out.writeLong(model.seed); out.writeBoolean(model.seedKnown); out.writeBoolean(model.historyKnown); out.writeBoolean(model.startingKingLost);
            out.writeInt(model.rulesVersion); out.writeInt(model.aiVersion); out.writeInt(model.intercepted); out.writeInt(model.cappedReinforcements);
            out.writeInt(model.objectiveType); out.writeInt(model.objectiveTarget); out.writeFloat(model.objectiveSeconds); out.writeFloat(model.objectiveProgress);
            out.writeInt(model.deploymentBudget); out.writeInt(model.challengeId); out.writeUTF(model.dailyDate);
            Object random = field(GameModel.class,"random").get(model); out.writeLong(field(random.getClass(),"state").getLong(random));
        }
        if (version == 5) {
            out.writeInt(model.outcome != 0 && model.terminalReason == 0 ? GameModel.TERMINAL_LEGACY : model.terminalReason);
            out.writeInt(model.missionConfigVersion); out.writeInt(model.missionPressure); out.writeUTF(model.dailyVersion);
            out.writeInt(model.battleMode); out.writeInt(model.runPerks); out.writeUTF(model.runId); out.writeInt(model.runNode);
        }
        out.flush(); return bytes.toByteArray();
    }
    private static void ints(DataOutputStream out, int[] values) throws IOException { out.writeInt(values.length); for (int value : values) out.writeInt(value); }
}
