/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.provisioners.PeProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

import java.util.*;

/**
 * VmSchedulerTimeShared is a Virtual Machine Monitor (VMM) allocation policy that allocates one or more PEs
 * from a PM to a VM, and allows sharing of PEs by multiple VMs. This class also implements 10% performance degradation due
 * to VM migration. This scheduler does not support over-subscription.
 * <p>
 * Each host has to use is own instance of a VmScheduler
 * that will so schedule the allocation of host's PEs for VMs running on it.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class VmSchedulerTimeShared extends VmScheduler {

    /**
     * The map of requested mips, where each key is a VM
     * and each value is a list of MIPS requested by that VM.
     */
    //各个Vm都请求了不同Pe的多少MIPs
    private Map<String, List<Double>> mipsMapRequested;

    /**
     * The number of host's PEs in use.
     */
    private int pesInUse;

    /**
     * Instantiates a new vm time-shared scheduler.
     *
     * @param pelist the list of PEs of the host where the VmScheduler is associated to.
     */
    public VmSchedulerTimeShared(List<? extends Pe> pelist) {
        super(pelist);
        setMipsMapRequested(new HashMap<String, List<Double>>());
    }

    public VmSchedulerTimeShared(List<? extends Pe> pelist, int id) {
        super(pelist, id);
        setMipsMapRequested(new HashMap<String, List<Double>>());
    }

    @Override
    //给Vm的vCPU分配多个Pe的MIPs，分配之前，要先通过该函数维护MigratingOut列表
    //（MigrationIn列表在HostDynamicWorkload的updateCloudletProcessing函数更新了）
    //mipsShareRequested为Vm各vCPU的请求量（其size是从cloudletScheduler那边获取过来的，
    // 所以就是Constants文件设置的vm的vCPU的数量）
    public boolean allocatePesForVm(Vm vm, List<Double> mipsShareRequested) {
        /*
         * @todo add the same to RAM and BW provisioners
         */
        if (vm.isInMigration()) {
            //既不在该Host原来的MigrationIn里面，也不在该Host的MigrationOut里面，
            // 但是该Vm又是在迁移状态的话，证明该VM准备要进行迁出
            if (!getVmsMigratingIn().contains(vm.getUid()) && !getVmsMigratingOut().contains(vm.getUid())) {
                //System.out.println("Vm #"+vm.getId()+"被加入到MigratingOut列表中");
                getVmsMigratingOut().add(vm.getUid());
            }
        } else {//VM不在迁移阶段

            //如果VM不在迁移阶段，且Vm的MigrationOut里面有该Vm，则进入该分支（经我修改后不会出现这种情况了）
            //这句通过打印日志观察到，这种情况之所以会触发是因为 上一次 optimizeAllocation Vm# X 从 A 迁移到 B
            //然后 之后的某次 optimizeAllocation Vm# X 又从 B 回迁到 A
            if (getVmsMigratingOut().contains(vm.getUid())) {
                System.out.println("这里是不合理的，不可能Vm迁出以后还发生这种情况");//修改点（我也不知道为什么，不管了）
                System.out.println("还是顺手打印一下现在的VmsMigratingOut是啥样的：");
                for (String vmuid : getVmsMigratingOut()) {
                    System.out.println("Vm #" + vmuid);
                }
                System.exit(1);

                //getVmsMigratingOut().remove(vm.getUid());//既然回迁了，那就让这个Vm老老实实呆着呗

            }
        }
        boolean result = allocatePesForVm(vm.getUid(), mipsShareRequested);
        updatePeProvisioning();
        return result;
    }

    /**
     * Allocate PEs for a vm.
     *
     * @param vmUid              the vm uid
     * @param mipsShareRequested the list of mips share requested by the vm
     * @return true, if successful
     */
    //mipsShareRequested是Cloudlet向Vm各vCPU申请的MIPs，也是Vm向Host各物理内核Pe申请的MIPs
    //vCPU分配到具体的哪个Pe上，以及相应Pe的MIPs数更新操作，在UpdatePeProvisioning函数中执行，
    //该函数仅仅用于更新“Host实际会给VM的各vCPU分配多少MIPs（用mipsMap记录）”以及“整个Host各个Pe总共剩余的可用MIPs数”
    //mipsShareRequested的size为Vm的vCPU数量
    protected boolean allocatePesForVm(String vmUid, List<Double> mipsShareRequested) {
        double totalRequestedMips = 0;
        double peMips = getPeCapacity();
        //VM每个虚拟内核必须小于Host实际内核的MIPs容量
        for (Double mips : mipsShareRequested) {
            // each virtual PE of a VM must require not more than the capacity of a physical PE
            if (mips > peMips) {
                return false;
            }
            totalRequestedMips += mips;
        }

        // This scheduler does not allow over-subscription
        // VM各核请求的总MIPs数要少于Host的可用MIPs数量，则分配失败，因为没设置超分
        if (getAvailableMips() < totalRequestedMips) {
            return false;
        }

        //如果放置成功，则更新Host的MIPs请求列表
        getMipsMapRequested().put(vmUid, mipsShareRequested);
        //这里这步对于TimeShared来说是多余的，可以删掉
        //TimeShared怎么可能一个Pe只能跑一个vCPU，SpaceShared策略才是这样分配的
        setPesInUse(getPesInUse() + mipsShareRequested.size());

        if (getVmsMigratingIn().contains(vmUid)) {
            // the destination host only experience 10% of the migrating VM's MIPS
            totalRequestedMips *= 0.1;
        }

        //Host实际需要给vm各vCPU分配的MIPs数，size等于Vm的vCPU数量
        List<Double> mipsShareAllocated = new ArrayList<Double>();
        for (Double mipsRequested : mipsShareRequested) {
            //请求的MIPs数是一回事，实际分配的MIPs数又是另一回事（取决于该VM是否刚刚迁入或准备迁出）
            if (getVmsMigratingOut().contains(vmUid)) {
                // performance degradation due to migration = 10% MIPS
                mipsRequested *= 0.9;
            } else if (getVmsMigratingIn().contains(vmUid)) {
                // the destination host only experience 10% of the migrating VM's MIPS
                mipsRequested *= 0.1;
            }
            mipsShareAllocated.add(mipsRequested);
        }

        getMipsMap().put(vmUid, mipsShareAllocated);
        setAvailableMips(getAvailableMips() - totalRequestedMips);

        return true;
    }

    /**
     * Update allocation of VMs on PEs.
     * @too The method is too long and may be refactored to make clearer its
     * responsibility.

    //根据目前的mipsMap（Host上各VM的MIPs请求）重新分配资源（这个是我自己重新实现的）
    //但是这个函数不能用，因为即便是VmSchedulerOverSubscription，仍然可能会存在碎片，
    //导致最后有vCPU放不下，这就出事儿了，严重影响某个用户的使用体验（该函数有待改进）
    protected void updatePeProvisioning() {
    //Host中全部Pe上Vm的MIPs分配推倒重来
    getPeMap().clear();
    for (Pe pe : getPeList()) {
    pe.getPeProvisioner().deallocateMipsForAllVms();
    }

    for (Map.Entry<String, List<Double>> entry : getMipsMap().entrySet()) {
    String vmUid = entry.getKey();
    getPeMap().put(vmUid, new LinkedList<Pe>());//重新构造特定Vm的Pe分配（Pe里面含MIPs）
    //获取Host的Pe实例
    Iterator<Pe> peIterator = getPeList().iterator();
    Pe pe = peIterator.next();
    PeProvisioner peProvisioner = pe.getPeProvisioner();
    double availableMips = peProvisioner.getAvailableMips();//单个Pe还有多少可用的MIPs

    for(double mips:entry.getValue()){//逐个vCPU尝试放置到不同的Pe内核中
    while(mips>=0.1) {
    if (availableMips >= mips) {//只要该Pe的可用MIPs多于该vCPU的MIPs就可以放置
    peProvisioner.allocateMipsForVm(vmUid, mips);
    getPeMap().get(vmUid).add(pe);
    availableMips -= mips;
    break;
    }
    else{
    //我的算法调度时不会出现这种情况，但是难保由于负载波动导致不够MIPs的情况出现
    //不过我的算法使用超分策略，最后会按比例缩减MIPs，所以不可能进入!peIterator.hasNext()这个分支
    if (!peIterator.hasNext()) {
    Log.printConcatLine("updatePeProvisioning：存在vCPU怎么都放不下，出问题了", vmUid);
    System.exit(0);
    }
    pe = peIterator.next();
    peProvisioner = pe.getPeProvisioner();
    availableMips = peProvisioner.getAvailableMips();
    }
    }
    }
    }
    }*/

    /**
     * Update allocation of VMs on PEs.
     *
     * @too The method is too long and may be refactored to make clearer its
     * responsibility.
     */
    //根据目前的mipsMap（Host上各VM的MIPs请求）重新分配资源，Host中全部Pe上Vm的MIPs分配推倒重来
    //因为mipsMap只是VmScheduler其中一个变量，PeList需要另外进行维护：这个函数就是用于维护PeList的
    protected void updatePeProvisioning() {

        //Host中全部Pe上Vm的MIPs分配推倒重来
        getPeMap().clear();
        for (Pe pe : getPeList()) {
            pe.getPeProvisioner().deallocateMipsForAllVms();
        }

        //这函数打算对服务器的逐个Pe进行处理
        Iterator<Pe> peIterator = getPeList().iterator();
        Pe pe = peIterator.next();
        PeProvisioner peProvisioner = pe.getPeProvisioner();
        double availableMips = peProvisioner.getAvailableMips();//单个Pe还有多少可用的MIPs

		/*//Debug
		for (Map.Entry<String, List<Double>> entry : getMipsMap().entrySet()) {
			System.out.println("VM #"+entry.getKey()+" 请求了以下的MIPs");
			int i=0;
			for(double mips : entry.getValue()){
				System.out.println("List["+i+"]="+mips);
				i++;
			}
		}*/

        //获取各Vm的vCPU请求量，并分配到Vm中，注意，同一个Vm的不同vCPU可以分配到同一个Pe里面
        //这里的策略是填满了服务器的一个核，再使用服务器的另一个核
        for (Map.Entry<String, List<Double>> entry : getMipsMap().entrySet()) {
            String vmUid = entry.getKey();
            //重新构造特定Vm的Pe分配（Pe里面含MIPs），其中LinkedList中的下标代表着
            // Vm的第几个vCPU，而存放的Pe则是Host的Pe实例
            getPeMap().put(vmUid, new LinkedList<Pe>());
            for (double mips : entry.getValue()) {//entry.getValue()得到的是Vm各vCPU的MIPs请求
                while (mips >= 0.1) {//因为后续出现一个Pe MIPs不够时，entry的这个vCPU只会分到该Pe剩余的可用MIPs，
                    //该vCPU剩余的MIPs请求量如果少于0.1就直接不管了，如果是大于0.1就继续取下一个Pe，
                    //将剩余的MIPs分给该vCPU（事实上这样做并不合理，不知道现实场景中能不能这样强行
                    //把一个VM的vCPU分配到Host的两个Pe上，而且把服务器的部分Pe搞的满载了，其他Pe却是空
                    //载的，这根本不合适，搞的一个Pe是满载的话，负载波动一下这个Pe就超负荷了，严重不合理）

                    //Debug
                    //System.out.println("目前Vm #" + vmUid + "一个vCPU的mips请求数为：" + mips);
                    //System.out.println("Pe# " + pe.getId() + " 的availableMIPs为 " + availableMips);

                    //修改点：这种情况会在上个vCPU刚刚好，在下述的availableMips >= mips分支处，把一个Pe的MIPs用完的时候出现
                    if (availableMips == 0) {
                        if (!peIterator.hasNext()) {
                            Log.printConcatLine("There is no enough MIPS (", mips, ") to accommodate VM ", vmUid);
                            //Debug
                            System.out.println("<UpdatePeProvisioning>出问题了，超分情况下MIPs不够？！打印一下当前Host的Pe分配情况 以及 MIPs请求总量：");
                            double MIPsReqSum = 0;
                            for (Map.Entry<String, List<Double>> tmpEntry : getMipsMap().entrySet()) {
                                //System.out.println("VM #"+tmpEntry.getKey()+"请求的MIPs如下所示：");
                                int i = 0;
                                for (Double a : tmpEntry.getValue()) {
                                    MIPsReqSum += a;
                                    //System.out.println("List["+i+"]="+a);
                                    i++;
                                }
                            }
                            System.out.println("\nHost的MIPs容量为：" + getPeCapacity() * getPeList().size());
                            System.out.println("总的MIPs请求量为：" + MIPsReqSum);
                            break;//修改点：强行中断放置
                        }

                        //取该Host的下一个Pe，继续分配MIPs给vCPU
                        pe = peIterator.next();
                        peProvisioner = pe.getPeProvisioner();
                        availableMips = peProvisioner.getAvailableMips();
                    }

                    if (availableMips >= mips) {//只要该Pe的可用MIPs多于该vCPU的MIPs就可以放置
                        /*boolean isAllocateSuccessful = peProvisioner.allocateMipsForVm(vmUid, mips);//修改点
                        if(!isAllocateSuccessful){
                            System.out.println("怎么可能放置失败？？分支1");
                            System.exit(1);
                        }*/
                        peProvisioner.allocateMipsForVm(vmUid, mips);

                        getPeMap().get(vmUid).add(pe);
                        availableMips -= mips;
                        //setAvailableMips(getAvailableMips()-mips);

                        /*//Debug
                        System.out.println("Vm #" + vmUid + "的一个 vCPU 的MIPs放置后，PeTable的亚子：");
                        for (Double val : peProvisioner.getPeTableByVmUid(vmUid)) {
                            System.out.println(val);
                        }*/

                        break;
                    } else {//如果该Pe剩余的MIPs不足以分配给entry这个VM的一个vCPU，那就只能将剩余的可用MIPs全部分给这个VM的vCPU
                        //然后取下一个Pe进行放置该vCPU的MIPs
                        /*boolean isAllocateSuccessful = peProvisioner.allocateMipsForVm(vmUid, availableMips);//修改点
                        if(!isAllocateSuccessful){//修改点：有特殊情况，如果availableMips刚好为0，该Vm在该Pe上是不会分配到任何MIPs的
                            System.out.println("怎么可能放置失败？？分支2");
                            System.exit(1);
                        }*/
                        peProvisioner.allocateMipsForVm(vmUid, availableMips);

                        getPeMap().get(vmUid).add(pe);
                        mips -= availableMips;
                        //setAvailableMips(getAvailableMips()-mips);//修改点
                        if (mips <= 0.1) {
                            break;
                        }

                        /*//Debug
                        System.out.println("Vm #" + vmUid + "的一个 vCPU 的MIPs放置后，PeTable的亚子：");
                        for (Double val : peProvisioner.getPeTableByVmUid(vmUid)) {
                            System.out.println(val);
                        }*/

                        //我的算法调度时不会出现这种情况，但是难保由于负载波动导致不够MIPs
                        //不过我的算法使用超分策略，最后会按比例缩减MIPs，所以不可能进入!peIterator.hasNext()这个分支
                        if (!peIterator.hasNext()) {
                            Log.printConcatLine("There is no enough MIPS (", mips, ") to accommodate VM ", vmUid);
                            //Debug
                            System.out.println("<UpdatePeProvisioning>出问题了，超分情况下MIPs不够？！" +
                                    "打印一下当前Host的Pe分配情况 以及 MIPs请求总量：");
                            double MIPsReqSum = 0;
                            for (Map.Entry<String, List<Double>> tmpEntry : getMipsMap().entrySet()) {
                                //System.out.println("VM #"+tmpEntry.getKey()+"请求的MIPs如下所示：");
                                int i = 0;
                                for (Double a : tmpEntry.getValue()) {
                                    MIPsReqSum += a;
                                    //System.out.println("List["+i+"]="+a);
                                    i++;
                                }
                                //System.out.println();
                            }
                            System.out.println("\nHost的MIPs容量为：" + getPeCapacity() * getPeList().size());
                            System.out.println("总的MIPs请求量为：" + MIPsReqSum);
                            break;//修改点：强行中断放置
                        }
                        //取该Host的下一个Pe，继续分配MIPs给vCPU
                        pe = peIterator.next();
                        peProvisioner = pe.getPeProvisioner();
                        availableMips = peProvisioner.getAvailableMips();
                    }
                }
            }
            /*//Debug
            if (entry.getKey().equals("2-30")) {
                System.out.println("\nVM #" + entry.getKey() + " 的Pe分配如下");
                int previousPeId = -1;
                for (Pe tmpPe : getPeMap().get(vmUid)) {
                    if (previousPeId == tmpPe.getId()) continue;
                    else {
                        previousPeId = tmpPe.getId();
                        for (Double val : tmpPe.getPeProvisioner().getPeTableByVmUid(vmUid)) {
                            System.out.println("Pe# " + previousPeId + " 分配了 " + val + "MIPs");
                        }
                    }
                }
            }*/
        }
    }

    @Override
    //用于清除Pe给Vm的分配关系，更新可用Mips、Pe数据和映射关系
    //不过这个函数写的很暴力，删一个Vm就直接删掉清空Hashmap（mipsMap），
    //然后Host上所有VM的vCPU重新分配给Pe（估计是因为受到OverloadDegradation的影响，所以打算全部MIPs重新分配一遍）
    //PS：需要维护MipsMapRequested数组
    public void deallocatePesForVm(Vm vm) {
        getMipsMapRequested().remove(vm.getUid());//把Host分配给vm各vCPU的MIPs数组删掉
        setPesInUse(0);//后续在allocatePesForVm会重新设置该值
        getMipsMap().clear();
        setAvailableMips(PeList.getTotalMips(getPeList()));//因为mipsMap重置了，Host的可用MIPs也重置

        //逐个Pe检查，有没有分配给该Vm的MIPs，如果有就把MIPs回收（主要是维护PeTable和Host.availableMips
        for (Pe pe : getPeList()) {
            pe.getPeProvisioner().deallocateMipsForVm(vm);
        }

        //重新放置所有VM的vCPU
        for (Map.Entry<String, List<Double>> entry : getMipsMapRequested().entrySet()) {
            allocatePesForVm(entry.getKey(), entry.getValue());
        }

        updatePeProvisioning();//更新Pe的信息
    }

    /**
     * Releases PEs allocated to all the VMs.
     *
     * @pre $none
     * @post $none
     */
    @Override
    public void deallocatePesForAllVms() {
        super.deallocatePesForAllVms();
        getMipsMapRequested().clear();
        setPesInUse(0);
    }

    /**
     * Returns maximum available MIPS among all the PEs. For the time shared policy it is just all
     * the avaiable MIPS.
     *
     * @return max mips
     */
    @Override
    public double getMaxAvailableMips() {
        return getAvailableMips();
    }

    /**
     * Sets the number of PEs in use.
     *
     * @param pesInUse the new pes in use
     */
    protected void setPesInUse(int pesInUse) {
        this.pesInUse = pesInUse;
    }

    /**
     * Gets the number of PEs in use.
     *
     * @return the pes in use
     */
    protected int getPesInUse() {
        return pesInUse;
    }

    /**
     * Gets the mips map requested.
     *
     * @return the mips map requested
     */
    protected Map<String, List<Double>> getMipsMapRequested() {
        return mipsMapRequested;
    }

    /**
     * Sets the mips map requested.
     *
     * @param mipsMapRequested the mips map requested
     */
    protected void setMipsMapRequested(Map<String, List<Double>> mipsMapRequested) {
        this.mipsMapRequested = mipsMapRequested;
    }

}
