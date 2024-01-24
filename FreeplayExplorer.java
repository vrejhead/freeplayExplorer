import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.json.*;

class FreeplayExplorer {
    // i gregging HATE this oop stuff but 
    public static void main(String[] args) {
        HashMap<String, Integer> bloonRBE = new HashMap<>();
        bloonRBE.put("Red", 1);
        bloonRBE.put("Blue", 2);
        bloonRBE.put("Green", 3);
        bloonRBE.put("Yellow", 4);
        bloonRBE.put("Pink", 5);
        bloonRBE.put("Black", 11);
        bloonRBE.put("White", 11);
        bloonRBE.put("Purple", 11);
        bloonRBE.put("Zebra", 23);
        bloonRBE.put("Lead", 23);
        bloonRBE.put("LeadFortified", 26);
        bloonRBE.put("Rainbow", 47);
        bloonRBE.put("Ceramic", 104);
        bloonRBE.put("CeramicFortified", 114);
        bloonRBE.put("Moab", 616);
        bloonRBE.put("MoabFortified", 856);
        bloonRBE.put("Bfb", 3164);
        bloonRBE.put("BfbFortified", 4824);
        bloonRBE.put("Zomg", 16656);
        bloonRBE.put("ZomgFortified", 27296);
        bloonRBE.put("Ddt", 816);
        bloonRBE.put("DdtFortified", 1256);
        bloonRBE.put("Bad", 55760);
        bloonRBE.put("BadFortified", 98360);

        List<JSONObject> freeplayGroups = new ArrayList<>();
        List<String> emissions = new ArrayList<>();
        try {
            String text = Files.readString(Path.of("freeplayGroups.json"));
            JSONArray tempGroup = new JSONArray(text);
            for (int i = 0; i < tempGroup.length(); i++) {
                freeplayGroups.add(tempGroup.getJSONObject(i));
            }

        } catch (Exception e) {
            System.out.printf("failed to read json file with exception %s", e);
            return;
        }
        Scanner scan = new Scanner(System.in);
        System.out.println("ENTER ROUND HERE: ");
        final int ROUND = Integer.parseInt(scan.nextLine());
        System.out.println("ENTER SEED HERE:");
        final int SEED = Integer.parseInt(scan.nextLine());
        SeededRandom random = new SeededRandom(ROUND + SEED);
        ShuffleSeeded(freeplayGroups, ROUND + SEED);
        float budget = (float) (CalculateBudget(ROUND) * (1.5 - random.getNext()));
        for (JSONObject object : freeplayGroups) {
            boolean inBounds = false;
            JSONArray bounds = object.getJSONArray("bounds");
            for (int i = 0; i < bounds.length(); i++) {
                if ((bounds.getJSONObject(i).getInt("lowerBounds") <= ROUND) && (ROUND <= bounds.getJSONObject(i).getInt("upperBounds"))) {
                    inBounds = true;
                    break;
                }
            }
            if (!inBounds) {
                continue;
            }
            float score = CalculateScore(object, bloonRBE);
            if (score > budget) continue;
            emissions.add(object.getString("name"));
            System.out.println(object.get("name"));
            budget -= score;
        }
    }

    static class SeededRandom {
        int initialSeed;
        long currentSeed;

        public SeededRandom(int seed) {
            this.initialSeed = seed;
            this.currentSeed = seed;
        }

        public float getNext() {
            this.currentSeed  = (this.currentSeed * 0x41a7) % 0x7FFFFFFF;
            return (float) (this.currentSeed * 4.656613e-10);
        }

        public double getNextDouble() {
            this.currentSeed  = (this.currentSeed * 0x41a7) % 0x7FFFFFFF;
            return this.currentSeed / 2147483646.0;
        }
    }

    public static <T> List<T> ShuffleSeeded(List<T> lst, int seed) {
        SeededRandom random = new SeededRandom(seed);
        int pointer = lst.size() - 1;
        int listLen = pointer;
        while (true) {
            double rand = random.getNextDouble();
            int index = (int) (listLen * rand);
            T temp = lst.get(pointer);
            lst.set(pointer, lst.get(index));
            lst.set(index, temp);
            pointer -= 1;
            if (pointer < 0) {
                return lst;
            }
        }
    }
    public static float CalculateBudget(int round) {
        if (round > 100) {
            return round * 4000 - 225000;
        }
        double budget = Math.pow(round, 7.7);
        double helper = Math.pow(round, 1.75);
        if (round > 50) {
            return (float) (budget * 5e-11 + helper + 20);
        }
        return (float) ((1 + round * 0.01) * (round * -3 + 400) * ((budget * 5e-11 + helper + 20) / 160) * 0.6);
    }
    public static float CalculateScore(JSONObject bloonModel, HashMap<String, Integer> bloonRBE) {
        String bloon = bloonModel.getJSONObject("group").getString("bloon");
        int count = bloonModel.getJSONObject("group").getInt("count");
        double multiplier = 1;
        if (bloon.contains("Camo")) {
            multiplier += 0.1;
            bloon = bloon.replace("Camo", "");
        }
        if (bloon.contains("Regrow")) {
            multiplier += 0.1;
            bloon = bloon.replace("Regrow", "");
        }
        if (count == 1) return (float) (bloonRBE.get(bloon) * multiplier);
        double spacing = ((double) bloonModel.getJSONObject("group").getInt("end")) / (60 * count);
        double totalRBE = count * bloonRBE.get(bloon) * multiplier;
        if (spacing >= 1) return (float) (totalRBE * 0.8);
        if (spacing >= 0.5) return (float) (totalRBE);
        if (spacing > 0.1) return (float) (totalRBE * 1.1);
        if (spacing > 0.08) return (float) (totalRBE * 1.4);
        return (float) (totalRBE * 1.8);
    }
}
