package org.cloudbus.cloudsim.cooling;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import org.cloudbus.cloudsim.HostDynamicWorkload;
import org.cloudbus.cloudsim.HostStateHistoryEntry;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.text.DecimalFormat;
/**
 * 热模型（计算节点入口和主机温度）
 * @ author: lin jianpeng
 * @ date: 2023/4/6
 */
public class ThermalModel {
    private static final double HostInletTemperatureRedline=25.0;   //主机入口红线温度 25 摄氏度
    public static final double HostTemperatureRedline=75.0;        //主机红线温度 75 摄氏度
    public static final int NUMBER_OF_HOSTS=800; //主机数(要跟Constants.java设置的对应上)
    public static final int NUMBER_OF_RACKS=20; //机架数
    private static final int NUMBER_OF_LOCATIONS=3; //机架位置
    private  ArrayList<String> TsupHistory;     // Tsup参数历史记录
    private  ArrayList<String> FsHistory;       // FanSpeed参数历史记录
    private  ArrayList<String> TemperatureDistributiuonHistory;       //机房温度分布历史记录
    /** MutilSetPointCoolingPolicy=false:全局统一设定，最小能耗)；true:多设定点，在全局设定参数再进一步微调)*/
    private boolean MutilSetPointCoolingPolicy=true;     // 是否采用细粒度控制方法（全局+微调）
    /**
        构建一个主机与机架的映射. 按照id顺序分配到各个机架上
        表示为(1:"rack_0_2") 其中,1:表示主机ID; 0_2表示机架0，位置2;
     */
    public static final Map<Integer,String> RackHostMap=new HashMap<Integer, String>(){{
        for(int host_id =0;host_id<NUMBER_OF_HOSTS;host_id++){
            int rack_no = host_id % NUMBER_OF_RACKS;
            int rack_loc = host_id % NUMBER_OF_LOCATIONS;
            put(host_id, "rack_"+ rack_no +"_"+rack_loc);
        }
    }};
    /**
     * 构建一个CRAC与机架的关联映射，用于根据主机热约束来调整最相关CRAC的冷却参数
     * 1、根据CFD仿真软件来确定CRAC对机架的影响权重；
     * 2、采用数据来计算CRAC对机架的影响权重；
     */
    private static final Map<Integer,ArrayList<Integer>> CracRacksMap=new HashMap<Integer, ArrayList<Integer>>(){{
        // CRAC_ID：Rack_ID 表示CRAC_ID冷却覆盖的机架Rack_ID；（采用CFD的CRAC影响区域功能验证）
        ArrayList<Integer> crac0Racks=new ArrayList<Integer>();ArrayList<Integer> crac1Racks=new ArrayList<Integer>();
        ArrayList<Integer> crac2Racks=new ArrayList<Integer>();ArrayList<Integer> crac3Racks=new ArrayList<Integer>();
        crac0Racks.add(0); crac1Racks.add(5); crac1Racks.add(10); crac1Racks.add(15);
        crac0Racks.add(1); crac1Racks.add(6); crac1Racks.add(11); crac3Racks.add(16);
        crac0Racks.add(2); crac1Racks.add(7); crac1Racks.add(12); crac3Racks.add(17);
        crac2Racks.add(3); crac2Racks.add(8); crac2Racks.add(13); crac3Racks.add(18);
        crac2Racks.add(4); crac2Racks.add(9); crac2Racks.add(14); crac3Racks.add(19);

        put(0, crac0Racks);
        put(1, crac1Racks);
        put(2, crac2Racks);
        put(3, crac3Racks);

    }};

    public CRACs cracs;
    // 构造函数
    public ThermalModel() {
        TsupHistory=new ArrayList<>();
        FsHistory=new ArrayList<>();
        TemperatureDistributiuonHistory=new ArrayList<>();
        cracs=new CRACs();
    }
    /**
     * 获得主机的活跃时间，模仿Helper的getTimesBeforeHostShutdown
     */
    protected double getHostActiveTime(PowerHost host){
        double timeBeforeShutdown=0;
        boolean previousIsActive = true;
        double lastTimeSwitchedOn = 0;
        for (HostStateHistoryEntry entry : ((HostDynamicWorkload) host).getStateHistory()) {
            if (previousIsActive && !entry.isActive()) {
                timeBeforeShutdown=entry.getTime() - lastTimeSwitchedOn;
            }
            if (!previousIsActive && entry.isActive()) {
                lastTimeSwitchedOn = entry.getTime();
            }
            previousIsActive = entry.isActive();
        }
        return timeBeforeShutdown;
    }



    /**
     * 根据主机列表计算各个机架的总功率
     * @param hosts
     * @return racks power
     */
    public double[] getRacksPower(List<PowerHost> hosts){
        double[] RacksPower=new double[20];
        for (PowerHost host:hosts) {
            double power = host.getPower();
            int id = host.getId();
            String rack = this.RackHostMap.get(id);
            String[] rack_loc = rack.split("_"); //1:"rack_2_3" 1表示主机ID,2_3表示机架2，位置3
            int no = Integer.parseInt(rack_loc[1]);
            RacksPower[no]+=power;
        }
        return RacksPower;
    }

    /**
     * 计算所有主机入口温度
     */
    public ArrayList<Double> getHostsInletTempeture(List<PowerHost> hosts,double[] Tsups, double[] FSs) throws ModelNotFoundException, MalformedModelException, IOException, TranslateException {
        ArrayList<Double> HostsInletTemperature= new ArrayList<>(); // 主机入口温度列表
        //调用热模型进行温度预测
        NDManager manager = NDManager.newBaseManager();
        Criteria<NDList,NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(Paths.get("modules/cloudsim/src/main/modelFiles/ANN.pt"))
                .optModelName("ANN_1")
                .build();
        ZooModel model = criteria.loadModel();
        // 特征数组
        float input[] = new float[28];      // 输入的特征属性
        for(int i=0;i<8;i+=2){// 前8个特征是风扇转速(先)和供给温度（后）
            input[i]=(float)FSs[i/2];
            input[i+1]=(float)Tsups[i/2];
        }
        //System.out.println("input:"+Arrays.toString(input));

        double[] racksPower = this.getRacksPower(hosts);
        for(int i=8;i<28;i++){// 后20个特征是机架功率
            input[i]=(float)racksPower[i-8]/1000;
        }
        // 模型预测
        NDArray singleInput = manager.create(input);
        Predictor<NDList,NDList> predictor = model.newPredictor();
        NDList result = predictor.predict(new NDList(singleInput));
        float[] resultArray = result.get(0).toFloatArray();
        for (float v : resultArray) {
            HostsInletTemperature.add((double)v);
        }
        model.close();
        return HostsInletTemperature;
    }

    /**
     * 计算所有主机温度列表
     */
    public ArrayList<Double> getHostsTempeture(List<PowerHost> hosts,double[] Tsups, double[] FSs) throws TranslateException, ModelNotFoundException, MalformedModelException, IOException {
        //记录所有主机温度
        ArrayList<Double> HostsTemperature= new ArrayList<>();
        //计算主机入口温度
        ArrayList<Double> HostsInletTemperature=this.getHostsInletTempeture(hosts,Tsups,FSs);
        // 采用RC热模型计算各个主机的温度
        for (PowerHost host : hosts) {
            // 根据主机的放置位置确定主机入口温度Tinlet，作为RC模型的输入
            double Tinlet=this.getHostInletTemperatureById(host.getId(),HostsInletTemperature);
            double hostTemp = 0.0;//主机温度
            hostTemp = RC_thermalmodel(host, Tinlet);
            HostsTemperature.add(hostTemp);
        }
        return HostsTemperature;
    }

    /**
     * 根据主机的放置位置确定主机入口温度Tinlet
     */
    public double getHostInletTemperatureById(int id,ArrayList<Double> HostsInletTemperature){
        double Tinlet=0.0;
        String rack = this.RackHostMap.get(id);
        String[] rack_loc = rack.split("_"); //1:"rack_2_3" 1表示主机ID,2_3表示机架2，位置3
        int no = Integer.parseInt(rack_loc[1]);
        int loc= Integer.parseInt(rack_loc[2]);
        int index=no*3+loc;
        Tinlet=HostsInletTemperature.get(index);
        return Tinlet;
    }

    /**
     * RC thermal model
     */
    public double RC_thermalmodel(PowerHost host ,double Tinlet){
        Tinlet+=273.15;                 //摄氏度 转换成 K
        double e = 2.718;               // 常量
        double P=host.getPower();       //主机的动态功率
        double R=0.34;                  //热阻，按照论文ETAS取0.34K/W，即0.34度/W
        double C=340;                   //热容，按照论文ETAS取340J/K，即340j/度
        double T0=318;                  //CPU的初始温度设置成35.0摄氏度较合适（按照论文ETAS设置为318K，即44.85摄氏度）
//        double time=getHostActiveTime(host); // 主机活动时间（调度间隔300s）
        double time=300; // 主机活动时间（调度间隔300s）
        double hostTemp=P*R+Tinlet+(T0-P*R-Tinlet)*Math.pow(e,-time/(R*C));
        hostTemp-=273.15;               //K 换回摄氏度
        return hostTemp;
    }
    /**
     * 计算上个时间片的冷却能耗增量
     * @param timeFrameITEnergy
     * @return
     */
    public double getTimeFrameCoolingEnergy(double timeFrameITEnergy) {
        double coolingPower = cracs.getCoolingPower(timeFrameITEnergy);
        return coolingPower;
    }
    /**
     * 更新CRACs冷却参数设定点(基于启发式冷却控制方法)
     * Step1:基于入口温度的热约束来确定全局冷却设定参数(所有CRAC的设定参数一致)；
     * Step2:根据各个区域与红线温度的差值来作为调整该区域CRAC的值,
     * 具体来说，（红线温度-区域温度）正差值越大，说明该区域存在过度冷却，因此可设当调高温度，以减少能耗
     */
    public void UpdateCoolingSetpoints(List<PowerHost> hostList) throws TranslateException, ModelNotFoundException, MalformedModelException, IOException {
        // 记录CRAC的冷却参数和温度分布
        this.TsupHistory.add(Arrays.toString(cracs.getTsups()));
        this.FsHistory.add(Arrays.toString(cracs.getFSs()));
        ArrayList<Double> TemperatureDistributiuon = this.getHostsInletTempeture(hostList,cracs.getTsups(),cracs.getFSs());
        // ArrayList转成String
        Double[] array_TD= new Double[TemperatureDistributiuon.size()];
        array_TD = (Double[]) TemperatureDistributiuon.toArray(array_TD);
        this.TemperatureDistributiuonHistory.add(Arrays.toString(array_TD));

        double currentTime = CloudSim.clock();
        /**Step1:先统计各个机架的总功率，然后作为热模型的输入，以计算满足热约束的新的供给温度和风扇的新的设定点（最小功耗）*/
        // 构建可用冷却设定点数组
        double[] Tsup_points = new double[ 2*(int)(cracs.MAX_TSUP-cracs.MIN_TSUP)+1];
        double[] FS_points = new double[10];
        for(int i =0;i<2*(int)(cracs.MAX_TSUP-cracs.MIN_TSUP)+1;i++){
            Tsup_points[i]=cracs.MIN_TSUP+i*0.5;
        }
        for(int i =0;i<10;i++){
            FS_points[i]=(double) Math.round((cracs.MIN_FS*i+0.1) * 10) / 10;  // 只保留小数点后一位，不然后面Map取值会报错
        }
        //计算当前机房的所有机架功率
        double[] racksPower = getRacksPower(hostList);
        double sumITPower=0.0;
        for (double rp:racksPower) {
            sumITPower+=rp;
        }
        /*遍历所有供给温度和风扇转速的组合，然后选取满足热约束，且能耗最小的组合作为新的冷却设定点*/
        //最大冷却功耗
        double[] CRACs_MIN_TUPs=new double[]{cracs.MIN_TSUP,cracs.MIN_TSUP,cracs.MIN_TSUP,cracs.MIN_TSUP};
        double[] CRACs_MAX_FSs=new double[]{cracs.MAX_FS,cracs.MAX_FS,cracs.MAX_FS,cracs.MAX_FS};
        double MaxCoolingPower=cracs.getCoolingPower(sumITPower,CRACs_MIN_TUPs,CRACs_MAX_FSs);

        double[] global_Tsups=CRACs_MIN_TUPs;
        double[] global_FSs=CRACs_MAX_FSs;

        //Step1:基于主机入口温度的热约束来确定全局冷却设定参数(所有CRAC的设定参数一致)；
        for(int i=0;i<Tsup_points.length;i++){
            for(int j=0;j<FS_points.length;j++){
                //临时 冷却设定参数
                double[] tem_Tsups=new double[]{Tsup_points[i],Tsup_points[i],Tsup_points[i],Tsup_points[i]};
                double[] tem_FSs=new double[]{FS_points[j],FS_points[j],FS_points[j],FS_points[j]};
                // 判断该冷却设点下是否出现热风险，若无风险则继续计算功耗，若出现，则不考虑；
                ArrayList<Double> hostsInletTempeture = this.getHostsInletTempeture(hostList,tem_Tsups,tem_FSs);
                boolean risk = this.hasThermalRisk(hostsInletTempeture);
                if(!risk){ // 无热风险，计算该冷却设定组合的功耗.
                    double tempPower=cracs.getCoolingPower(sumITPower,tem_Tsups, tem_FSs);
                    if(tempPower<MaxCoolingPower){
                        MaxCoolingPower=tempPower;
                        global_Tsups=tem_Tsups;
                        global_FSs=tem_FSs;
                    }
                }
            }
        }
//        System.out.println("全局冷却设定："+Arrays.toString(global_Tsups)+"-"+Arrays.toString(global_FSs));
        DecimalFormat df = new DecimalFormat("#.0"); //使double保留一位小数
        // Step2:根据各个区域与红线温度的差值来作为调整该区域CRAC的设定点。
        if(this.MutilSetPointCoolingPolicy){
            // 计算在全局参数设定下各主机的入口温度分布
            ArrayList<Double> hostsInletTempeture = this.getHostsInletTempeture(hostList,global_Tsups,global_FSs);
            // 将入口温度分布划分成4个区域，计算各个区域平均温度与入口红线温度（HostInletTemperatureRedline=25）的差值；
            // 根据温差值大小，返回排序的4个关联CRAC_IDs数组
            int[] SortedCracIDs=this.getSortedCracIDsByRegionalTemperatureDifference(hostsInletTempeture);
            // 按照区域温差值从大到小的排序调整所关联的CRAC冷却参数（温度（+1）风扇转速（-0.1）），直至出现热风险，则保存其临界设定值；

            double[] tem_global_Tsups=global_Tsups;
            double[] tem_global_FSs=global_FSs;
            for (int id=0;id<SortedCracIDs.length;id++) {// 按温差大小顺序调节4个CRAC的参数。
                //微调温度，在起始温度基础上递增+0.5，并检测是否出现热风险，一旦出现，则-0.5，并结束while循环
                while(tem_global_Tsups[id]<cracs.MAX_TSUP){
                    tem_global_Tsups[id]+=0.5;//温度+0.5
                    ArrayList<Double> temp_hostsInletTempeture1 = this.getHostsInletTempeture(hostList,tem_global_Tsups,tem_global_FSs);
                    if(this.hasThermalRisk(temp_hostsInletTempeture1)){
                        tem_global_Tsups[id]-=0.5;
                        break;
                    }
                }
                //微调风扇,在起始转速基础上递减-0.1，并检测是否出现热风险，一旦出现，则+0.1，并结束while循环
                while(tem_global_FSs[id]>0.1 && tem_global_FSs[id]<=1.0){
                    tem_global_FSs[id]= Double.parseDouble(df.format(tem_global_FSs[id]-0.1));//转速-0.1
                    ArrayList<Double> temp_hostsInletTempeture2 = this.getHostsInletTempeture(hostList,tem_global_Tsups,tem_global_FSs);
                    if(this.hasThermalRisk(temp_hostsInletTempeture2)){
                        tem_global_FSs[id]=Double.parseDouble(df.format(tem_global_FSs[id]+0.1));
                        break;
                    }
                }
                /** 若前面调节CRAC参数后已经不能满足热约束，那后面就不调了*/
                ArrayList<Double> temp_hostsInletTempeture3 = this.getHostsInletTempeture(hostList,tem_global_Tsups,tem_global_FSs);
                if(this.hasThermalRisk(temp_hostsInletTempeture3)){
                    break;
                }
            }
            global_Tsups=tem_global_Tsups;
            global_FSs=tem_global_FSs;
        }
//        System.out.println("微调后冷却设定："+Arrays.toString(global_Tsups)+"-"+Arrays.toString(global_FSs));
        //重设CRAC的冷却参数
        cracs.setTsups(global_Tsups);
        cracs.setFSs(global_FSs);

        Log.formatLine("\n%.2f :Tsup is set to %.2f, Fanspeed is set to %.2f\n", currentTime,cracs.getTsups()[0],cracs.getFSs()[0]);
        System.out.println(currentTime+":Tsup is set to "+Arrays.toString(cracs.getTsups())+", Fanspeed is set to"+Arrays.toString(cracs.getFSs()));
    }

    private int[] getSortedCracIDsByRegionalTemperatureDifference(ArrayList<Double> hostsInletTempeture) {
        double[] TemperatureDifference=new double[4];
        // 根据CracRacksMap获取各个CRAC所覆盖的机架入口温度
        for (int crac_id:CracRacksMap.keySet()) {
            ArrayList<Integer> rack_ids = CracRacksMap.get(crac_id);//各个CRAC所覆盖的所有机架IDs
            ArrayList<Double> rack_temps=new ArrayList<>();//记录覆盖的所有机架入口温度值
            for (int rcak_id:rack_ids) {//获取每个机架的3个温度值
                rack_temps.add(hostsInletTempeture.get(3*rcak_id));
                rack_temps.add(hostsInletTempeture.get(3*rcak_id+1));
                rack_temps.add(hostsInletTempeture.get(3*rcak_id+2));
            }
            //crac所覆盖区域的机架入口温度平均值
            double average_temp=rack_temps.stream().mapToDouble(a->a).sum()/rack_temps.size();
            //记录每个crac区域的温度差值
            TemperatureDifference[crac_id]=average_temp-this.HostInletTemperatureRedline;
        }
        //对温度差值数组进行排序，按照从大到小，返回index(crac_id)
        int[] SortedCracIDs = Arraysort(TemperatureDifference, true);
        return SortedCracIDs;
    }

    /**
     * 判断是否出现热风险（若有1/10主机入口温度超过红线温度阈值则判定为热风险）
     * @return true/false
     */
    public boolean hasThermalRisk(ArrayList<Double> HostsInletTemperature){
        int count=0;
        for (double hit:HostsInletTemperature) {
            if (hit > this.HostInletTemperatureRedline) {
                count+=1;
            }
        }
        if(count>(HostsInletTemperature.size()/10)){
            return true;
        }
        return false;
    }
    /**
     * 计算当前时间片的主机热点数
     * @param hostList
     * @return
     */
    public int getTimeFrameHostspotNum(List<PowerHost> hosts) throws TranslateException, ModelNotFoundException, MalformedModelException, IOException {
        ArrayList<Double> hostsTempeture = getHostsTempeture((ArrayList<PowerHost>) hosts, cracs.getTsups(), cracs.getFSs());
        int hotspotNum=0;
        for (double ht:hostsTempeture) {
            if(ht>this.HostTemperatureRedline){
                hotspotNum+=1;
            }
        }
        return hotspotNum;
    }
    /**
     *  计算满足主机入口的热约束（Tin<Tredline）的最高供给温度。
     */
    public double getMaxTsupWithThermalConstrain(List<PowerHost> hostList) throws TranslateException, ModelNotFoundException, MalformedModelException, IOException {
        double MaxTsup=cracs.MAX_TSUP;//从最大供给温度开始，不断减低温度直到无热风险
        while(true){
            double[] temp_Tsup={MaxTsup,MaxTsup,MaxTsup,MaxTsup};
            double[] temp_FS={0.5,0.5,0.5,0.5};
            ArrayList<Double> hostsInletTempeture = getHostsInletTempeture(hostList, temp_Tsup, temp_FS);
            if (hasThermalRisk(hostsInletTempeture)){
                if (MaxTsup>cracs.MIN_TSUP){//若设定供给温度大于CRAC得最小温度则继续降低温度，否则返回最小供给温度
                    MaxTsup-=0.5;
                }else {
                    MaxTsup=cracs.MIN_TSUP;
                    break;
                }
            }else{
                break;
            }
        }
        return MaxTsup;
    }


    public ArrayList<String> getTsupHistory() {
        return TsupHistory;
    }

    public void setTsupHistory(ArrayList<String> tsupHistory) {
        TsupHistory = tsupHistory;
    }

    public ArrayList<String> getFsHistory() {
        return FsHistory;
    }

    public void setFsHistory(ArrayList<String> fsHistory) {
        FsHistory = fsHistory;
    }

    public ArrayList<String> getTemperatureDistributiuonHistory() {
        return TemperatureDistributiuonHistory;
    }

    public void setTemperatureDistributiuonHistory(ArrayList<String> temperatureDistributiuonHistory) {
        TemperatureDistributiuonHistory = temperatureDistributiuonHistory;
    }

    /**
     * 排序并返回对应原始数组的下标
     *
     * @param arr
     * @param desc 默认降序
     * @return
     */
    public static int[] Arraysort(double[] arr, boolean desc) {
        double temp;
        int index;
        int k = arr.length;
        int[] Index = new int[k];
        for (int i = 0; i < k; i++) {
            Index[i] = i;
        }
        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr.length - i - 1; j++) {
                if (desc) {
                    if (arr[j] < arr[j + 1]) {
                        temp = arr[j];
                        arr[j] = arr[j + 1];
                        arr[j + 1] = temp;

                        index = Index[j];
                        Index[j] = Index[j + 1];
                        Index[j + 1] = index;
                    }
                } else {
                    if (arr[j] > arr[j + 1]) {
                        temp = arr[j];
                        arr[j] = arr[j + 1];
                        arr[j + 1] = temp;
                        index = Index[j];
                        Index[j] = Index[j + 1];
                        Index[j + 1] = index;
                    }
                }
            }
        }
        return Index;
    }

}
