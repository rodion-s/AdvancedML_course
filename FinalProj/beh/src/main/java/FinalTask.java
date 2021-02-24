import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

public class FinalTask extends Configured implements Tool {
    public static class Session {
	    String[] show;
	    int[] clickedPos;
	    HashMap<String, Integer> time;
	    public Session() {}
	    public Session(String session) {
		String[] Split = session.trim().split("\t");

		show = Split[1].replace("http://","").replace("https://","").replace("www.", "").split(",");
		String[] clicks = Split[2].replace("http://","").replace("https://","").replace("www.", "").split(",");
		String[] timestamps = Split[3].split(",");
		for (int i = 0; i < show.length; i++) {
		    if (show[i].endsWith("/")) {
		        show[i] = show[i].substring(0, show[i].length() - 1);
		    }
		}
		for (int i = 0; i < clicks.length; i++) {
		    if (clicks[i].endsWith("/")) {
		        clicks[i] = clicks[i].substring(0, clicks[i].length() - 1);
		    }
		}
		clickedPos = new int[clicks.length];
		int timestamp =  Integer.parseInt(timestamps[0]);
		for (int i = 0; i < clicks.length; i++) {
		    for (int j = 0; j < show.length; j++) {
		        if (clicks[i].equals(show[j])) {
		            if (i < clicks.length - 1) {
		                int diff =  Integer.parseInt(timestamps[j + 1]) - timestamp;
		            	time.put(show[j], diff);
		            	timestamp = diff;
		            }
		            clickedPos[i] = j;
		            break;
		        }
		    }
		}
	    } 
     }
    
    public static class FinalTaskMapper extends Mapper<LongWritable, Text, IntWritable, Text> {
        private final HashMap<String, Integer> urlsMap = new HashMap<>();
        private final HashMap<String, Integer> queryMap = new HashMap<>();
        private boolean hostFeaturestype = false;
        private void makeMapFromFile(BufferedReader reader, HashMap<String, Integer> map) throws IOException {
            String line = reader.readLine();
            while (line != null) {
                String[] splited = line.trim().split("\t");
                Integer id = Integer.parseInt(splited[0]);
                String url = splited[1];
                url = url.replace("http://","").replace("https://","").replace("www.", "");
                if (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                map.put(url, id);
                line = reader.readLine();
            }
            reader.close();
        }
        @Override
        protected void setup(Context context) throws IOException {
            hostFeaturestype = context.getConfiguration().get(FEATURES_TYPE).equals("host");
            String urlsFileName = context.getConfiguration().get(URLS_FILE);
            FileSystem fs = FileSystem.get(new Configuration());
            BufferedReader urlsFileReader = new BufferedReader(new InputStreamReader(fs.open(new Path(urlsFileName))));
            
            makeMapFromFile(urlsFileReader, urlsMap);
            String queriesFileName = context.getConfiguration().get(QUERIES_FILE);
            BufferedReader queriesFileReader = new BufferedReader(new InputStreamReader(fs.open(new Path(queriesFileName))));
            makeMapFromFile(queriesFileReader, queryMap);
        }
      private StringBuilder makeValue(Session session, int pos, String url) {
            StringBuilder res = new StringBuilder();
            res.append(pos); 
            res.append(" ");
            int clickIdx = -1;
            for (int i = 0; i < session.clickedPos.length; i++) {
                if (session.clickedPos[i] == pos) {
                    clickIdx = i;
                    break;
                }
            }
            if (clickIdx == -1) {
                res.append(0);
            } else {
                res.append(1);
            }
            res.append(" ");
            res.append(session.clickedPos[session.clickedPos.length - 1]);
            res.append(" ");
            if (session.time.containsKey(url)) {
            	res.append(session.time.get(url));
            } else {
            	res.append(0);
           }
            return res;
        }
     @Override
     protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
         String sessionStr = value.toString();
         String query = new StringTokenizer(new StringTokenizer(sessionStr, "\t").nextToken(), "@").nextToken().trim();
         Session session = new Session(sessionStr);
         for (int i = 0; i < session.show.length; i++) {
             String url = session.show[i];
              if (hostFeaturestype) {
                   try {
                      url = new URL("http://" + url).getHost();
                   } catch (Exception e) {
                       continue;
                    }
               }
               if (urlsMap.containsKey(url)) {
                  int urlId = urlsMap.get(url);
                   StringBuilder res = makeValue(session, i, url);
                   context.write(new IntWritable(urlId), new Text(res.toString()));
                }
          }
      }  
    }

    public static class FinalTaskReducer extends Reducer<IntWritable, Text, IntWritable, Text> {
        private MultipleOutputs<IntWritable, Text> out;
        private final HashSet<String> trainSet = new HashSet<>();
        private final HashSet<String> testSet = new HashSet<>();
        @Override
        protected void setup(Context context) throws IOException {
            out = new MultipleOutputs<>(context);
        }
        @Override
        protected void reduce(IntWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
             long shows = 0;
	     long clicks = 0;
	     long dbnShows = 0;
	     long dbnClicks = 0;
	     long dbnLastClick = 0;
	     long time = 0;
	     long posSum = 0;
            for (Text v: values) {
                String[] splited = v.toString().split(" ");
                int pos = Integer.parseInt(splited[0]);

                byte click = Byte.parseByte(splited[1]);
                int lastClickPos = Integer.parseInt(splited[2]);
                int time_spent = Integer.parseInt(splited[3]);
                shows++;
		clicks += click;
		if (pos <= lastClickPos) {
		    dbnShows++;
		    dbnClicks += click;
		}
		if (pos == lastClickPos) {
		     dbnLastClick++;
		 }
		posSum += pos;
		time += time_spent;
            }
 	    StringBuilder res = new StringBuilder();
           res.append(shows).append("\t");
           res.append(clicks).append("\t");
           res.append(dbnShows).append("\t");
           res.append(dbnClicks).append("\t");
           res.append(dbnLastClick).append("\t");
           res.append(((double) posSum) / shows).append("\t");
           if (clicks > 0) {
		res.append(((double) time) / clicks);
	   } else {
		res.append(0);
	   }
            out.write(GLOBAL_FEATURES_FILE, key, new Text(res.toString()));
        }
    }
    private Job getFinalTaskJobConf(String input, String output) throws IOException {
        Job job = Job.getInstance(getConf());
        job.setJarByClass(FinalTask.class);
        job.setJobName(FinalTask.class.getCanonicalName());
        job.setInputFormatClass(TextInputFormat.class);
        TextInputFormat.addInputPath(job, new Path(input));
        FileOutputFormat.setOutputPath(job, new Path(output));
        MultipleOutputs.addNamedOutput(job, GLOBAL_FEATURES_FILE, TextOutputFormat.class, IntWritable.class, Text.class);
        job.setMapperClass(FinalTaskMapper.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setReducerClass(FinalTaskReducer.class);
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(13);
        return job;
    }

    @Override
    public int run(String[] args) throws Exception {
        Job job = getFinalTaskJobConf(args[0], args[1]);
        job.getConfiguration().set(URLS_FILE, args[2]);
        job.getConfiguration().set(QUERIES_FILE, args[3]);
        job.getConfiguration().set(FEATURES_TYPE, args[6]);
        return job.waitForCompletion(true) ? 0 : 1;
    }
    static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
    public static void main(String[] args) throws Exception {
        deleteDirectory(new File(args[1]));
        int exitCode = ToolRunner.run(new FinalTask(), args);
        System.exit(exitCode);
    }
    private static final String URLS_FILE = "";
    private static final String QUERIES_FILE = "";
    private static final String GLOBAL_FEATURES_FILE = "global";
    private static final String FEATURES_TYPE = "";  
}

