package org.cloudbus.cloudsim.experiment;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class CreateMixWorkload {
    private static int NewWorkloadSize = 2000;

    public static void main(String[] args) throws IOException {
        //读取planetlab的路径，并记录到不同的File[]里面
        ArrayList<File[]> fileListList = new ArrayList<>();

        String inputFolder = DePso.class.getClassLoader().getResource("workload/planetlab").getPath();
        inputFolder = URLDecoder.decode(inputFolder, "UTF-8");//解决中文路径问题
        File PlanetLabPath = new File(inputFolder);
        File[] OutterPathList = PlanetLabPath.listFiles();
        ArrayList<String> workloadName = new ArrayList<>();
        //获取不同文件夹的名字（20110303等）
        for (File file : OutterPathList) {
            workloadName.add(file.getName());
        }

        //读取文件并记录其文件数分段值
        ArrayList<Integer> SegmentDevideValue = new ArrayList<>();
        for (int i = 0; i < workloadName.size() - 1; i++) {//有一个是Mix的，要去掉
            //记录不同workload的File[]
            File workloadPath = new File(inputFolder + "/" + workloadName.get(i));
            File[] tmpWorkloadFile = workloadPath.listFiles();
            fileListList.add(tmpWorkloadFile);
            //记录workload的文件数分段值
            if (i == 0) SegmentDevideValue.add(tmpWorkloadFile.length);
            else SegmentDevideValue.add(tmpWorkloadFile.length + SegmentDevideValue.get(i - 1));
        }

        //System.out.println(SegmentDevideValue.get(9));
        //构造新的数据集，每2000个数据为一个数据集
        String outputFolder = DePso.class.getClassLoader().getResource("workload").getPath();
        outputFolder = URLDecoder.decode(outputFolder, "UTF-8");//解决中文路径问题
        //System.out.println(outputFolder);
        outputFolder+="/Mix";
        File getPath = new File(outputFolder);
        if(!getPath.exists()) getPath.mkdir();

        File createOutputFolder=new File(outputFolder);
        if(!createOutputFolder.exists()) createOutputFolder.mkdir();
        Random rand = new Random(5);
        for (int i = 0; i < 10; i++) {
            String outputPath = outputFolder + "/" + "Mix-" + i;
            File createOutputPath=new File(outputPath);
            if(!createOutputPath.exists()) createOutputPath.mkdir();
            HashMap<Integer, Boolean> record = new HashMap<>();//用于记录该数据集中被选中过的workload文件的序号
            for (int j = 0; j < NewWorkloadSize; j++) {
                //随机选择11848个文件中的一个（不重复的）
                int randNum = rand.nextInt(SegmentDevideValue.get(9));
                int k = -1;
                //通过比对文件数分段值，判断该数字属于第几天的workload
                for (k = 0; k < SegmentDevideValue.size(); k++) {
                    if (k == 0) {
                        if (randNum >= 0 && randNum < SegmentDevideValue.get(0)) {
                            break;
                        }
                    } else {
                        if (randNum >= SegmentDevideValue.get(k - 1) && randNum < SegmentDevideValue.get(k)) {
                            break;
                        }
                    }
                }
                if (k == -1) {
                    System.out.println("An error occurred. The appropriate <workload> was not selected");
                }
                //记录被选中的数字
                if (record.get(randNum) == null) {
                    record.put(randNum, true);
                } else {//选到重复的workload就重选
                    j--;
                    continue;
                }
                if (k > 0) randNum -= SegmentDevideValue.get(k - 1);
                //获取被选中的文件的路径
                String oldPath = fileListList.get(k)[randNum].getAbsolutePath();
                String newPath = outputPath + "/" + k + "_" + fileListList.get(k)[randNum].getName();
                copy(oldPath,newPath);
            }
        }

        /*
        String oldPath = "D:/DESKTOP/75-130-96-13_static_oxfr_ma_charter_com_utokyo_sora";
        String newPath = "D:/DESKTOP/1";
        copy(oldPath, newPath);*/
    }

    //用于复制文件的函数
    private static void copy(String oldpath, String newpath) throws IOException {
        File oldpaths = new File(oldpath);
        File newpaths = new File(newpath);
        Files.copy(oldpaths.toPath(), newpaths.toPath());
    }
}
