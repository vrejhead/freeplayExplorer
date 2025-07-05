import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.json.*;

class FreeplayExplorer {
    // i gregging HATE this oop stuff but 
    public static void main(String[] args) {
        BloonCalculator bloonCalculator = new BloonCalculator();
        List<JSONObject> freeplayGroups = new ArrayList<>();
        try {
            String text = Files.readString(Path.of("FreeplayGroups.json"));
            JSONArray tempGroup = new JSONArray(text);
            for (int i = 0; i < tempGroup.length(); i++) {
                freeplayGroups.add(tempGroup.getJSONObject(i));
            }

        } catch (Exception e) {
            System.out.printf("failed to read json file with exception %s", e);
            return;
        }
//        int[] arguments = parseArguments(args);
        int[] arguments = { 98015596, 58, 58 };
        int seed = arguments[0];
        int start = arguments[1];
        int end = arguments[2];
        long totalRBE = 0;
        double totalCash = 0;
        int totalTime = 0;

        for (int round = start; round <= end; round++) {
            SeededRandom random = new SeededRandom(round + seed);
            List<Integer> testGroups = IntStream.range(0, 529).boxed().collect(Collectors.toList());
            shuffleTwo(testGroups, random);
            float budget;
            if (round > 1) {
                budget = (float) (calculateBudget(round) * (1.5 - random.getNext()));
            } else {
                budget = calculateBudget(round);
            }
            System.out.println(testGroups);

            float OGBudget = budget;
            long roundRBE = 0;
            double roundCash = 0;
            int roundTime = 0;
            System.out.println("+------------------------------------------------------+");
            System.out.printf("| ROUND %46s |\n", round);
            System.out.println("+------------------+-----------------+-----------------+");
            System.out.println("|            Bloon |           Count |          Length |");
            System.out.println("+------------------+-----------------+-----------------+");
            for (int index : testGroups) {
                JSONObject object = freeplayGroups.get(index);
                boolean inBounds = false;
                JSONArray bounds = object.getJSONArray("bounds");
                for (int i = 0; i < bounds.length(); i++) {
                    if ((bounds.getJSONObject(i).getInt("lowerBounds") <= round) && (round <= bounds.getJSONObject(i).getInt("upperBounds"))) {
                        inBounds = true;
                        break;
                    }
                }
                if (!inBounds) {
//                    System.out.println("skipped emission " + index + " bounds");
                    continue;
                }
                float score = object.getFloat("score") == 0 ? calculateScore(object, bloonCalculator, round) : object.getFloat("score");
                if (score > budget) {
//                    System.out.println("skipped emission " + index + " score " + score);
                    continue;
                }
                String bloon = object.getJSONObject("group").getString("bloon");
                int count = object.getJSONObject("group").getInt("count");
                roundRBE += (long) bloonCalculator.getRBE(bloon, round) * count;
                roundCash += bloonCalculator.getCash(bloon, round) * count;
                roundTime += object.getJSONObject("group").getInt("end");
                System.out.println(formatEmissions(object) + " " + score + " " + index);
                budget -= score;
            }
            System.out.println("+------------------------------------------------------+");
            System.out.printf("| %52s |\n", String.format("Score budget: %,.2f/%,.2f", OGBudget - budget, OGBudget));
            System.out.printf("| %52s |\n", String.format("Round RBE: %,d", roundRBE));
            System.out.printf("| %52s |\n", String.format("Round Cash: %,.2f", roundCash));
            System.out.printf("| %52s |\n", String.format("Round Length: %,d", roundTime));
            System.out.printf("| %52s |\n", String.format("Health Multiplier: %s", BloonCalculator.getHealthMultiplier(round)));
            System.out.printf("| %52s |\n", String.format("Speed Multiplier: %s", BloonCalculator.getSpeedMultiplier(round)));
            totalCash += roundCash;
            totalRBE += roundRBE;
            totalTime += roundTime;
        }
        System.out.println("+------------------------TOTAL-------------------------+");
        System.out.printf("| %52s |\n", String.format("Total RBE: %,d", totalRBE));
        System.out.printf("| %52s |\n", String.format("Total Cash: %,.2f", totalCash));
        System.out.printf("| %52s |\n", String.format("Total Time: %,d", totalTime));
        System.out.println("+------------------------------------------------------+");
    }

    static class SeededRandom {
        long seed;

        public SeededRandom(int seed) {
            this.seed = seed;
        }

        public void step() {
            seed = seed * 0x41a7 % 0x7fffffff;
        }

        public float getNext() {
            step();
            return (float) (this.seed * 4.656613e-10);
        }

        public double getNextDouble() {
            step();
            return this.seed / 2147483646.0;
        }

        public int getNext(int min, int max) {
            if (min == max) return min;
            int diff = min - max;
            step();
            int shift = Math.floorMod(seed, diff);
            if (shift == 0) shift += diff;
            return max + shift;
        }
    }

    public static int[] parseArguments(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("arguments must be in the form of {seed}, {startRound}, {endRound}, both inclusive");
        }
        return Arrays.stream(args).mapToInt(Integer::parseInt).toArray();
    }

    public static String formatEmissions(JSONObject emission) {
        JSONObject group = emission.getJSONObject("group");
        return String.format("| %16s |%16s |%16s |",
                group.getString("bloon"),
                group.getInt("count"),
                group.getInt("end")
        );
    }

    public static <T> void shuffleSeeded(List<T> lst, int seed) {
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
                return;
            }
        }
    }

    public static <T> void shuffleTwo(List<T> list, SeededRandom rng) {
        for (int i = 0; i < list.size(); i++) {
            int index = rng.getNext(i, list.size());
            Collections.swap(list, i, index);
        }
    }

    public static float calculateBudget(int round) {
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

    public static float calculateScore(JSONObject bloonModel, BloonCalculator calc, int round) {
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
        int bloonRBE = calc.getRBE(bloon, round);
        if (count == 1) return (float) (bloonRBE * multiplier);
        double spacing = ((double) bloonModel.getJSONObject("group").getInt("end")) / (60 * count);
        double totalRBE = count * bloonRBE * multiplier;
        if (spacing >= 1) return (float) (totalRBE * 0.8);
        if (spacing >= 0.5) return (float) (totalRBE);
        if (spacing > 0.1) return (float) (totalRBE * 1.1);
        if (spacing > 0.08) return (float) (totalRBE * 1.4);
        return (float) (totalRBE * 1.8);
    }

    /*
    removes the useless fields from the freeplayGroups json
     */
    public static void CleanJSON() {
        List<JSONObject> freeplayGroups = new ArrayList<>();
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
        JSONArray cleanedJSON = new JSONArray();
        BloonCalculator calc = new BloonCalculator();
        for (JSONObject freeplayGroup : freeplayGroups) {
            /*
            there are some emissions which [group][end] =/= lastEmission.time
            the game respects lastEmission.time so correct it
            example: element 188 in the freeplayGroups.json
            this seems to only happen with single spawn emissions
             */
            JSONArray bloonEmissions = freeplayGroup.getJSONArray("bloonEmissions");
            JSONObject lastEmission = bloonEmissions.getJSONObject(bloonEmissions.length() - 1);
            freeplayGroup.getJSONObject("group").put("end", lastEmission.getInt("time"));


            freeplayGroup.remove("bloonEmissions_"); //its all null
            freeplayGroup.remove("bloonEmissions"); // takes a lot of space with a lot of duplicate information
            // lists out each emission seperately even though they are all the same bloon and evenly spaced
            freeplayGroup.remove("checkedImplementationType"); //all null
            freeplayGroup.remove("ImplementationType"); //why is this one capitalized
            freeplayGroup.remove("implementationType"); //why is this one capitalized
            freeplayGroup.remove("childDependants"); //all null
            freeplayGroup.remove("_name"); // copy of "name"
            freeplayGroup.remove("WasCollected"); // something something but its always false
            freeplayGroup.getJSONObject("group").remove("start"); // always 0
            freeplayGroup.getJSONObject("group").remove("_name"); // copy of "name"
            freeplayGroup.getJSONObject("group").remove("ImplementationType");
            freeplayGroup.getJSONObject("group").remove("implementationType");
            freeplayGroup.getJSONObject("group").remove("childDependants");
            freeplayGroup.getJSONObject("group").remove("checkedImplementationType");
            freeplayGroup.getJSONObject("group").remove("WasCollected");
            if (freeplayGroup.getInt("score") == 0) {
                // todo fix this
//                freeplayGroup.put("score", calculateScore(freeplayGroup, calc));
            }
            cleanedJSON.put(freeplayGroup);
        }
        try {
            FileWriter writer = new FileWriter("cleanedFreeplayGroups.json");
            writer.write(cleanedJSON.toString());
            writer.close();
        } catch (Exception e) {
            System.out.printf("failed to write to cleanFreeplayGroups.json at the cwd, check your permissions %s", e);
        }
    }

    public static class BloonCalculator {
        JSONObject rawText;

        public BloonCalculator() {
            try {
                String text = Files.readString(Path.of("bloonData.json"));
                this.rawText = new JSONObject(text);
            } catch (Exception e) {
                System.out.printf("failed to read json file with exception %s", e);
                System.exit(1);
            }
        }

        public int getCash(String bloon, boolean isSuper) {
            JSONObject bloonData = rawText.getJSONObject(bloon);
            if (bloonData.getBoolean("isMoab")) {
                return bloonData.getInt("cash");
            }
            return bloonData.getInt(isSuper ? "superCash" : "cash");
        }

        public double getCash(String bloon, int round) {
            return getCash(bloon.replace("Fortified", "").replace("Camo", "").replace("Regrow", ""), round > 80) * getCashMultiplier(round);
        }

        public int getRBE(String bloon, double healthMultiplier, boolean isSuper, boolean isFortified) {
            JSONObject bloonData = rawText.getJSONObject(bloon);
            if (bloonData.getBoolean("isMoab")) {
                if (isFortified) {
                    return ((int) (bloonData.getInt("sumMoabHealth") * healthMultiplier * 2))
                            + bloonData.getInt("numCeramics") * rawText.getJSONObject("CeramicFortified").getInt(isSuper ? "superRBE" : "RBE");
                } else {
                    return ((int) (bloonData.getInt("sumMoabHealth") * healthMultiplier))
                            + bloonData.getInt("numCeramics") * rawText.getJSONObject("Ceramic").getInt(isSuper ? "superRBE" : "RBE");
                }
            }
            if (isFortified) {
                bloonData = rawText.getJSONObject(bloon + "Fortified");
            }
            return bloonData.getInt(isSuper ? "superRBE" : "RBE");
        }


        public int getRBE(String bloon, int round) {
            return getRBE(bloon.replace("Fortified", "").replace("Camo", "").replace("Regrow", ""), getHealthMultiplier(round), round > 80, bloon.contains("Fortified"));
        }

        public int getRBE(String bloon) {
            return getRBE(bloon, 1);
        }

        public static double getHealthMultiplier(int round) {
            if (round <= 80) return 1;
            if (round <= 100) return (round - 30) / 50D;
            if (round <= 124) return (round - 72) / 20D;
            if (round <= 150) return (3 * round - 320) / 20D;
            if (round <= 250) return (7 * round - 920) / 20D;
            if (round <= 300) return round - 208.5;
            if (round <= 400) return (3 * round - 717) / 2D;
            if (round <= 500) return (5 * round - 1517) / 2D;
            return 5 * round - 2008.5;
        }

        public static double getSpeedMultiplier(int round) {
            if (round <= 80) return 1;
            if (round <= 100) return 1 + (round - 80) * 0.02;
            if (round <= 150) return 1.6 + (round - 101) * 0.02;
            if (round <= 200) return 3 + (round - 151) * 0.02;
            if (round <= 250) return 4.5 + (round - 201) * 0.02;
            return 6 + (round - 252) * 0.02;
        }

        public static double getCashMultiplier(int round) {
            if (round <= 50) return 1;
            if (round <= 60) return 0.5;
            if (round <= 85) return 0.2;
            if (round <= 100) return 0.1;
            if (round <= 120) return 0.05;
            return 0.02;
        }
    }
}
