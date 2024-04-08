package de.dennisguse.opentracks.ui.aggregatedStatistics.SeasonStats;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Random;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.databinding.ActivitySeasonStatBinding;

public class SeasonStatActivity extends AppCompatActivity {

    ActivitySeasonStatBinding binding;


    public int calculateSum(ArrayList<Integer> list) {
        int sum = 0;
        for (int num : list) {
            sum += num;
        }
        return sum;
    }

    public static int findMax(ArrayList<Integer> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("ArrayList is empty or null");
        }

        int max = list.get(0);

        for (int i = 1; i < list.size(); i++) {
            int current = list.get(i);
            if (current > max) {
                max = current;
            }
        }
        return max;
    }

    public static int maxSpeed(ArrayList<Integer> dist, ArrayList<Integer> time){
        int max = (int)(dist.get(0)/time.get(0));
        for (int i = 1; i < dist.size(); i++) {
            int current = (int)(dist.get(i)/time.get(i));
            if (current > max) {
                max = current;
            }
        }
        return max;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySeasonStatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        String[] seasonNames = {"2020 - 2021 Season Stats", "2021 - 2022 Season Stats", "2022 - 2023 Season Stats", "2023 - 2024 Season Stats"};
        Random random = new Random();

        String[] Days = new String[4];
        int[] VerticalM = new int[4];
        int[] Runs = new int[4];
        double[] DistanceKM = new double[4];
        int[] Resort = new int[4];
        String[] Active = new String[4];
        double[] MaxSpeed = new double[4];
        int[] MaxAltitude = new int[4];
        int[] TallestRun = new int[4];
        double[] LongestRun = new double[4];


        int n = 10;
        ArrayList<Integer>[] VerticalMList = new ArrayList[n];
        int size = 10;

        for (int i = 0; i < n; i++) {
            VerticalMList[i] = new ArrayList<>();

            for (int j = 0; j < size; j++) {
                VerticalMList[i].add(random.nextInt(1000));
            }
        }

        ArrayList<Integer>[] DistanceKMs = new ArrayList[n];

        for (int i = 0; i < n; i++) {
            DistanceKMs[i] = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                DistanceKMs[i].add((int) (random.nextDouble() * 20));
            }
        }

        ArrayList<Integer>[] Hours = new ArrayList[n];
        ArrayList<Integer>[] Minutes = new ArrayList[n];
        ArrayList<Integer>[] Seconds = new ArrayList[n];

        for (int i = 0; i < n; i++) {
            Hours[i] = new ArrayList<>();
            Minutes[i] = new ArrayList<>();
            Seconds[i] = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                Hours[i].add((int) (1 + random.nextInt(23)));
                Minutes[i].add((int) (random.nextInt(60)));
                Seconds[i].add((int) (random.nextInt(60)));
            }
        }

        ArrayList<Integer>[] tallestRun = new ArrayList[n];

        for (int i = 0; i < n; i++) {
            tallestRun[i] = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                tallestRun[i].add((int) (random.nextDouble() * 100));
            }
        }

        ArrayList<Integer>[] longestRun = new ArrayList[n];

        for (int i = 0; i < n; i++) {
            longestRun[i] = new ArrayList<>();
            for (int j = 0; j < size; j++) {
                longestRun[i].add((int) (random.nextDouble() * 10));
            }
        }


        ArrayList<DummySeason> seasonArrayList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Days[i] = Integer.toString(random.nextInt(1001));
            VerticalM[i] = calculateSum(VerticalMList[i]);
            Runs[i] = calculateSum(longestRun[i]);
            DistanceKM[i] = calculateSum(DistanceKMs[i]);
            Resort[i] = random.nextInt(10) + 1;
            int hours = calculateSum(Hours[i]);
            int minutes = calculateSum(Minutes[i]);
            int seconds = calculateSum(Seconds[i]);
            String activeTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            Active[i] = activeTime;
            MaxSpeed[i] = maxSpeed(DistanceKMs[i],Hours[i]);
            TallestRun[i] = findMax(tallestRun[i]);
            MaxAltitude[i] = findMax(VerticalMList[i]);
            LongestRun[i] = findMax(longestRun[i]);

            seasonArrayList.add(new DummySeason(seasonNames[i], Integer.parseInt(Days[i]), Runs[i], Resort[i], VerticalM[i], DistanceKM[i], Active[i], MaxSpeed[i], MaxAltitude[i], TallestRun[i], LongestRun[i]));
        }

        SeasonListAdapter listAdapter = new SeasonListAdapter(SeasonStatActivity.this, seasonArrayList);
        binding.seasonListView.setAdapter(listAdapter);
        binding.seasonListView.setClickable(true);
        binding.seasonListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(SeasonStatActivity.this, RunAndChairStatActivity.class);
                intent.putExtra("seasonName",seasonArrayList.get(position).seasonName);
                intent.putExtra("days",seasonArrayList.get(position).days);
                intent.putExtra("runs",seasonArrayList.get(position).runs);
                intent.putExtra("resort",seasonArrayList.get(position).resort);
                intent.putExtra("vertical_m",seasonArrayList.get(position).vertical_m);
                intent.putExtra("distance",seasonArrayList.get(position).distance);
                intent.putExtra("active",seasonArrayList.get(position).active);
                intent.putExtra("max_Speed",seasonArrayList.get(position).max_Speed);
                intent.putExtra("max_alt",seasonArrayList.get(position).max_alt);
                intent.putExtra("tallestRun",seasonArrayList.get(position).tallestRun);
                intent.putExtra("longestRun",seasonArrayList.get(position).longestRun);
                startActivity(intent);
            }
        });
    }
}

//This class is each item in the list class which in our case is seasons. this is a dummy class just to have items You should use seasons in the code not ListItem class.
class DummySeason {
    String seasonName;
    int days;
    int runs;
    int resort;
    int vertical_m;
    double distance;
    String active;
    double max_Speed;
    int max_alt;
    int tallestRun;
    double longestRun;

    public DummySeason(String seasonName, int days, int runs, int resort, int vertical_m, double distance, String active, double max_Speed, int max_alt, int tallestRun, double longestRun) {
        this.seasonName = seasonName;
        this.days = days;
        this.runs = runs;
        this.resort = resort;
        this.vertical_m = vertical_m;
        this.distance = distance;
        this.active = active;
        this.max_Speed = max_Speed;
        this.max_alt = max_alt;
        this.tallestRun = tallestRun;
        this.longestRun = longestRun;
    }
}