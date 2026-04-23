package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.util.MathUtil;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * DDP算法专属的Host结构
 */
public class DDPHost {
    //静态数据
    public int HostId;
    public double CPU_Cap;//CPU容量（包含多个核）
    public double RAM_Cap;//RAM容量
    public double BW_Cap;//带宽容量
    public double peak_power;//峰值能耗（查表可得）
    public double peak_peff;//峰值效能比（查表可得）
    public double pp_CPU_util;//峰值效能比下的CPU利用率
    public double UP_THR;           //静态超载检测阈值(静态)
    private double[] standardPowerList;
    private double[] standardPeffList;


    //动态数据（浅拷贝的时候会用这个初始化）<Vm的Vcpu在Host的哪个Pe上的映射关系只存储在DDPVm里面，Host无需相关的Map>
    public HashMap<Integer, DDPVm> VmList = new HashMap<>();//Vm列表用HashMap进行存储，方便元素查找和频繁的删减操作
    public ArrayList<DDPPe> PeList = new ArrayList<>();//Host的内核列表

    public double CPU_util;//CPU利用率（包含多个核）
    public double RAM_util;//RAM利用率
    public double BW_util;//带宽利用率
    public double CPU_avail;//可用的RAM资源
    public double RAM_avail;//可用的RAM资源
    public double BW_avail;//可用的Band资源
    public double MIPs_offer;//剩余的高效CPU资源供应量（pp_CPU_util下的CPU资源供应量），有可能为负
    public double Power;//实时能耗
    public double Temperature; //实时主机温度
    public double Peff;//实时效能比（当前已用的MIPs/实时能耗Power）

    //构造函数
    public DDPHost(int HostId, double CPU_util, double RAM_util, double BW_util, double CPU_Cap, double RAM_Cap,
                   double BW_Cap, double peak_power, double peak_peff, double pp_CPU_util, HashMap<Integer, DDPVm> VmList,
                   ArrayList<DDPPe> peList, final double[] standardPowerList, final double[] standardPeffList, double temperature) {
        this.HostId = HostId;
        this.CPU_Cap = CPU_Cap;
        this.RAM_Cap = RAM_Cap;
        this.BW_Cap = BW_Cap;
        this.peak_power = peak_power;
        //this.peak_peff = peak_peff;
        this.peak_peff = (CPU_Cap * pp_CPU_util) / standardPowerList[(int) pp_CPU_util * 10];
        this.pp_CPU_util = pp_CPU_util;
        this.standardPowerList = standardPowerList;//反正List的值不会变，不用深拷贝
        this.standardPeffList = standardPeffList;
        this.UP_THR = Math.min(pp_CPU_util + 0.1, 0.75);

        for (Integer i : VmList.keySet()) {
            DDPVm tmp = new DDPVm(VmList.get(i));
            this.VmList.put(tmp.VmId, tmp);
        }
        for (int i = 0; i < peList.size(); i++) {
            DDPPe tmp = new DDPPe(peList.get(i).MIPs_Cap, peList.get(i).MIPs_available);
            this.PeList.add(tmp);
        }

        this.CPU_util = CPU_util;
        this.RAM_util = RAM_util;
        this.BW_util = BW_util;
        this.CPU_avail = (1 - CPU_util) * CPU_Cap;
        this.RAM_avail = (1 - RAM_util) * RAM_Cap;
        this.BW_avail = (1 - BW_util) * BW_Cap;
        this.MIPs_offer = (pp_CPU_util - CPU_util) * CPU_Cap;
        if (CPU_util != 0) Power = updatePower(CPU_util);
        else Power = 0;
        if (CPU_util != 0) Peff = updatePeff(CPU_util);
        else Peff = 0;
        this.Temperature=temperature;
    }


    //复制构造函数（有浅拷贝和深拷贝）
    //isDeepCopyVmListAndPeList==true则深拷贝两个List，isDeepCopyVmListAndPeList==false则不拷贝两个List
    public DDPHost(DDPHost a, boolean isDeepCopyVmListAndPeList) {
        HostId = a.HostId;
        CPU_Cap = a.CPU_Cap;
        RAM_Cap = a.RAM_Cap;
        BW_Cap = a.BW_Cap;
        peak_power = a.peak_power;
        peak_peff = a.peak_peff;
        pp_CPU_util = a.pp_CPU_util;
        UP_THR = a.UP_THR;
        this.standardPowerList = a.standardPowerList;//反正List的值不会变，直接浅拷贝
        this.standardPeffList = a.standardPeffList;

        if (isDeepCopyVmListAndPeList) {//深拷贝
            for (Integer VmId : a.VmList.keySet()) {
                DDPVm tmp = new DDPVm(a.VmList.get(VmId));
                this.VmList.put(tmp.VmId, tmp);
            }
            for (int i = 0; i < a.PeList.size(); i++) {
                DDPPe tmp = new DDPPe(a.PeList.get(i).MIPs_Cap, a.PeList.get(i).MIPs_available);
                this.PeList.add(tmp);
            }
        } else {
            //VmList就让它一直空着，但是Pe的基础属性要复制过来才行
            for (int i = 0; i < a.PeList.size(); i++) {
                DDPPe tmp = new DDPPe(a.PeList.get(i).MIPs_Cap, a.PeList.get(i).MIPs_Cap);
                this.PeList.add(tmp);
            }
        }

        CPU_util = a.CPU_util;
        RAM_util = a.RAM_util;
        BW_util = a.BW_util;
        CPU_avail = a.CPU_avail;
        RAM_avail = a.RAM_avail;
        BW_avail = a.BW_avail;
        MIPs_offer = a.MIPs_offer;
        Power = a.Power;
        Temperature=a.Temperature;
        Peff = a.Peff;

    }

    //复制函数（用于整个Vm放置流程后的更新，只复制动态数据即可）（浅拷贝）
    public void CopyUpdate(DDPHost a) {
        //更新动态数据
        VmList = a.VmList;
        PeList = a.PeList;

        CPU_util = a.CPU_util;
        RAM_util = a.RAM_util;
        BW_util = a.BW_util;
        CPU_avail = a.CPU_avail;
        RAM_avail = a.RAM_avail;
        BW_avail = a.BW_avail;
        MIPs_offer = a.MIPs_offer;
        Power = a.Power;
        Temperature=a.Temperature;
        Peff = a.Peff;
    }

    //将服务器的所有动态数据初始化
    public void initial() {
        //只需要初始化动态数据
        VmList = new HashMap<>();//这里千万别clear了，因为Vm选择策略中，计算Host性能退化的时候还要靠旧的Vm列表了恢复Host的状态
        for (DDPPe pe : PeList) {
            pe.MIPs_available = pe.MIPs_Cap;
        }
        CPU_util = 0;
        RAM_util = 0;
        BW_util = 0;
        CPU_avail = CPU_Cap;
        RAM_avail = RAM_Cap;
        BW_avail = BW_Cap;
        MIPs_offer = pp_CPU_util * CPU_Cap;
        Power = 0;
        Temperature=0.0;
        Peff = 0;
    }

    // 将Vm放置到Host，并更新所有动态数据，分两种情况
    //1、有PeMipsMap则需要按照PeMipsMap更新Pe的情况
    //2、没有PeMipsMap则不更新Pe的情况（说明调这个函数之前已经更新过了）
    public void placementUpdate(ArrayList<DDPPeMipsMapPair> PeMipsMap, DDPVm vm) {
        VmList.put(vm.VmId, vm);
        if (PeMipsMap != null) {
            for (DDPPeMipsMapPair pair : PeMipsMap) {
                PeList.get(pair.PeId).MIPs_available -= pair.PeMipsReq;
            }
        }
        CPU_avail -= vm.Total_CPU_Allocate;
        RAM_avail -= vm.RAM_Allocate;
        BW_avail -= vm.BW_Allocate;
        MIPs_offer -= vm.Total_CPU_Allocate;
        CPU_util = 1 - CPU_avail / CPU_Cap;
        RAM_util = 1 - RAM_avail / RAM_Cap;
        BW_util = 1 - BW_avail / BW_Cap;
        if (CPU_util != 0) Power = updatePower(CPU_util);
        else Power = 0;
        if (CPU_util != 0) Peff = updatePeff(CPU_util);
        else Peff = 0;
    }

    //进行数据的移除，所有Host相关的动态数据进行更新
    public void removeUpdate(DDPVm vm) {
        VmList.remove(vm.VmId);
        for (DDPPeMipsMapPair pair : vm.PeMipsMap) {
            PeList.get(pair.PeId).MIPs_available += pair.PeMipsReq;
        }
        CPU_avail += vm.Total_CPU_Allocate;
        RAM_avail += vm.RAM_Allocate;
        BW_avail += vm.BW_Allocate;
        MIPs_offer += vm.Total_CPU_Allocate;
        CPU_util = 1 - CPU_avail / CPU_Cap;
        RAM_util = 1 - RAM_avail / RAM_Cap;
        BW_util = 1 - BW_avail / BW_Cap;
        if (CPU_util != 0) Power = updatePower(CPU_util);
        else Power = 0;
        if (CPU_util != 0) Peff = updatePeff(CPU_util);
        else Peff = 0;
    }

    //Power计算公式
    public double updatePower(double utilization) throws IllegalArgumentException {
        if (utilization < 0 || utilization > 1) {
            //Debug：打印Host的信息
            System.out.println("utilization="+utilization);
            throw new IllegalArgumentException("Utilization value must be between 0 and 1");
        } else if (utilization == 0) return 0;//默认服务器利用率为0时关机
        else {
            utilization *= 100;//以百分比为单位，如34.3333%
            if (utilization % 10 == 0) {
                return standardPowerList[(int) (utilization / 10)];
            }
            int index1 = (int) Math.floor(utilization / 10);//向下取整，得到index
            int index2 = (int) Math.ceil(utilization / 10);//向上取整，得到index
            double power1 = standardPowerList[index1];
            double power2 = standardPowerList[index2];
            double utilization1 = index1 * 10;//左边界，以百分比为单位，如30%
            double delta = power2 - power1;
            //由于每一段都是线性的，所以可以用段内百分比来计算，比如(34.3333-30)/10得到段内的百分比，然后乘以这一段的Power差距
            double power = power1 + delta * (utilization - utilization1) / 10;
            return power;
        }
    }

    //Peff计算公式
    public double updatePeff(double utilization) throws IllegalArgumentException {
        return (CPU_Cap * pp_CPU_util - MIPs_offer) / Power;
    }

    /*//Peff计算公式（线性版）
    public double updatePeff(double utilization) throws IllegalArgumentException {
        if (utilization < 0 || utilization > 1) {
            throw new IllegalArgumentException("Utilization value must be between 0 and 1");
        } else if (utilization == 0) return 0;//默认服务器利用率为0时关机
        else {
            utilization *= 100;//以百分比为单位，如34.3333%
            if (utilization % 10 == 0) {
                return standardPeffList[(int) (utilization / 10)];
            }
            int index1 = (int) Math.floor(utilization / 10);//向下取整，得到index
            int index2 = (int) Math.ceil(utilization / 10);//向上取整，得到index
            double peff1 = standardPeffList[index1];
            double peff2 = standardPeffList[index2];
            double utilization1 = index1 * 10;//左边界，以百分比为单位，如30%
            double delta = peff2 - peff1;
            //由于每一段都是线性的，所以可以用段内百分比来计算，比如(34.3333-30)/10得到段内的百分比，然后乘以这一段的Power差距
            double peff = peff1 + delta * (utilization - utilization1) / 10;
            return peff;
        }
    }*/
}
