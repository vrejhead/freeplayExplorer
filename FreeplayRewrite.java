import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public class FreeplayRewrite {
    public static void main(String[] args) {
        int seed = 26512365;
        int round = 85;

        Emission[] emissions = Emission.load("freeplayGroups.json");
        int[] temp = new int[emissions.length];
        {
            SeededRandom rng = new SeededRandom(seed + round);
            Arrays.setAll(temp, i -> i);
            shuffle(temp, rng);
            float budget;
            if (round > 1) {
                budget = (float) (calculateBudget(round) * (1.5 - rng.getNext()));
            } else {
                budget = calculateBudget(round);
            }

            System.out.println(budget);

            for (int i : temp) {
                float score = round > 80 ? emissions[i].superScore : emissions[i].score;
                if (score > budget || !emissions[i].canSpawn(round)) {
                    continue;
                }

                budget -= score;
                System.out.println(emissions[i]);
            }
        }
    }
    public static void shuffle(int[] a, SeededRandom rng) {
        for (int i = 0; i < a.length; i++) {
            int index = rng.getNext(i, a.length);
            int temp = a[i];
            a[i] = a[index];
            a[index] = temp;
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

    public static class Emission {
        public final Bloon bloon;
        public final int count;
        public final int[] bounds;
        public final float score, superScore, time;
        public Emission(String bloon, int count, float time, float score, int[] bounds) {
            float mult = 1;
            if (bloon.contains("Camo")) {
                mult += 0.1f;
                bloon = bloon.replace("Camo", "");
            }
            if (bloon.contains("Regrow")) {
                mult += 0.1f;
                bloon = bloon.replace("Regrow", "");
            }
            this.bloon = Bloon.valueOf(bloon.toUpperCase(Locale.ROOT));
            this.count = count;
            this.time = time;
            this.score = score == 0 ? getScore(this.bloon.leak) * mult : score;
            this.superScore = score == 0 ? getScore(this.bloon.superLeak) * mult : score;
            this.bounds = bounds;
        }

        public float getScore(int leak) {
            if (count == 1) return leak;
            float spacing = time / (60f * count);
            int totalRBE = count * leak;
            if (spacing >= 1) return totalRBE * 0.8f;
            if (spacing >= 0.5) return totalRBE;
            if (spacing > 0.1) return totalRBE * 1.1f;
            if (spacing > 0.08) return totalRBE * 1.4f;
            return totalRBE * 1.8f;
        }

        public boolean canSpawn(int round) {
            int idx = Arrays.binarySearch(bounds, round);
            if (idx >= 0) return idx % 2 == 0;
            else return (-idx - 1) % 2 == 1;
        }

        @Override
        public String toString() {
            return count + "*" + bloon.toString() + "/" + time;
        }

        public static Emission[] load(String name) {
            try {
                JSONArray a = new JSONArray(Files.readString(Path.of(name)));
                Emission[] emissions = new Emission[a.length()];
                for (int i = 0; i < emissions.length; i++) {
                    JSONObject at = a.getJSONObject(i);
                    JSONObject group = at.getJSONObject("group");

                    JSONArray bounds = at.getJSONArray("bounds");
                    int[] arr = new int[2 * bounds.length()];
                    for (int j = 0; j < arr.length; j += 2) {
                        arr[j] = bounds.getJSONObject(j / 2).getInt("lowerBounds");
                        arr[j + 1] = bounds.getJSONObject(j / 2).getInt("upperBounds") + 1;
                    }

                    emissions[i] = new Emission(group.getString("bloon"), group.getInt("count"), group.getFloat("end"), at.getFloat("score"), arr);
                }
                return emissions;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @SuppressWarnings("unused")
    public enum Bloon {
        RED(1, 1),
        BLUE(2, 2),
        GREEN(3, 3),
        YELLOW(4, 4),
        PINK(5, 5),
        PURPLE(11, 6),
        WHITE(11, 6),
        BLACK(11, 6),
        LEAD(23, 7),
        LEADFORTIFIED(26, 10),
        ZEBRA(23, 7),
        RAINBOW(47, 8),
        CERAMIC(104, 65),
        CERAMICFORTIFIED(114, 75),
        MOAB(616, 460),
        MOABFORTIFIED(856, 700),
        BFB(3164, 2540),
        BFBFORTIFIED(4824, 4200),
        ZOMG(16656, 14160),
        ZOMGFORTIFIED(23296, 24800),
        DDT(816, 660),
        DDTFORTIFIED(1256, 1100),
        BAD(55760, 50300),
        BADFORTIFIED(90360, 92900);
        public final int leak, superLeak;
        Bloon(int leak, int superLeak) {
            this.leak = leak;
            this.superLeak = superLeak;
        }
    }

    public static class SeededRandom {
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
}
