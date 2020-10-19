import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
public class CpFile {
    public static void main(String[] args) throws Exception {
        Path path1 = new Path(args[0]), path2 = new Path(args[1]);
        Configuration conf = new Configuration();
	FileUtil.copy(path1.getFileSystem(conf), path1, 
                      path2.getFileSystem(conf), path2, 
                      false, conf);
    }
}
