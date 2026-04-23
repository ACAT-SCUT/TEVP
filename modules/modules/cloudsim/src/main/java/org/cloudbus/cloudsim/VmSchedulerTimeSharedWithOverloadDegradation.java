/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This is a Time-Shared VM Scheduler, which allows over-subscription. In other words, the scheduler
 * still allows the allocation of VMs that require more CPU capacity than is available.
 * Oversubscription results in performance degradation.
 *
 * @author Anton Beloglazov
 * @author Rodrigo N. Calheiros
 * @since CloudSim Toolkit 3.0
 */
//PM对VM的资源采取超分策略，用于更新mipsMap和Host各Pe的MIPs情况
//超载其实分为两种情况：一种是Vm的总MIPs数超过了Host的总MIPs数，另一种是某款Vm的MIPs数超过了该Host单个Pe的MIPs数
public class VmSchedulerTimeSharedWithOverloadDegradation extends VmSchedulerTimeShared {

    /**
     * Instantiates a new vm scheduler time shared over subscription.
     *
     * @param pelist the list of PEs of the host where the VmScheduler is associated to.
     */
    public VmSchedulerTimeSharedWithOverloadDegradation(List<? extends Pe> pelist, int id) {
        super(pelist, id);
    }

    public VmSchedulerTimeSharedWithOverloadDegradation(List<? extends Pe> pelist) {
        super(pelist);
    }

    /**
     * Allocates PEs for vm. The policy allows over-subscription. In other words, the policy still
     * allows the allocation of VMs that require more CPU capacity than is available.
     * Oversubscription results in performance degradation.
     * It cannot be allocated more CPU capacity for each virtual PE than the MIPS
     * capacity of a single physical PE.
     *
     * @param vmUid the vm uid
     * @param mipsShareRequested the list of mips share requested
     * @return true, if successful
     */
    /**
     * 注意这个函数在迁移VM调用时，是VM在源服务器和目标服务器是双开的，源服务器负担该迁移VM的0.9的Request
     * 另一个负担0.1的Request，等到VM迁移结束后的下一个时间片，该VM的资源分配才会恢复正常
     */
    //mipsShareRequested是Cloudlet向Vm各vCPU申请的MIPs，也是Vm向Host各物理内核Pe申请的MIPs
    //vCPU分配到具体的哪个Pe上，以及相应Pe的MIPs数更新操作，在UpdatePeProvisioning函数中执行，
    //该函数仅仅用于更新“Host实际会给VM的各vCPU分配多少MIPs（用mipsMap记录）”以及“整个Host各个Pe总共剩余的可用MIPs数”
    //mipsShareRequested的size为Vm的vCPU数量
    @Override
    protected boolean allocatePesForVm(String vmUid, List<Double> mipsShareRequested) {

        double totalRequestedMips = 0;

        // if the requested mips is bigger than the capacity of a single PE, we cap
        // the request to the PE's capacity
        //如果一个Vm请求的Mips大于单个PE的Mips容量，则只能用PE的Mips剩余容量覆盖该Vm的Mips请求量
        List<Double> mipsShareRequestedCapped = new ArrayList<Double>();
        double peMips = getPeCapacity();
        //这里的代码是为了避免，一个VM调度的时候，各vCPU的MIPs请求数很少，结果后来各vCPU的MIPs请求数暴增，
        //超过了该服务器一个Pe的容量，那么它实际能请求到的MIPs自然是不能超过一个Pe的请求数的，总不能将vCPU掰开两半吧？
        for (Double mips : mipsShareRequested) {//先统计一下该Vm总共能从Host能请求多少MIPs
            if (mips > peMips) {
                System.out.println("我的算法就不可能出现这种情况！！！！！");//修改点
                System.exit(1);//修改点
                mipsShareRequestedCapped.add(peMips);
                totalRequestedMips += peMips;
            } else {
                mipsShareRequestedCapped.add(Math.floor(mips));//修改点：增加了Math.floor
                totalRequestedMips += Math.floor(mips);
            }
        }

        //Debug
        //System.out.println("VM #" + vmUid + "分配到Host #" + Host_Belongs);
        //System.out.println("目前剩余的MIPs数为："+getAvailableMips());
        //System.out.println("Vm的MIPs请求量问："+totalRequestedMips);

        //反正OverloadDegradation的时候VM肯定能放得下，大不了砍掉一部分需求<超载出现性能退化>
        getMipsMapRequested().put(vmUid, mipsShareRequested);//在资源请求列表中新增该VM各vCPU的请求
        //设置有多少个Pe正在被使用（不过这句话不合理，TimeShared的不应该这么统计）
        setPesInUse(getPesInUse() + mipsShareRequested.size());

        //接下来构造Host真正分配给VM各vCPU多少MIPs的mipsMap(VmId,List<Double>)

        //如果该VM是在这个时间片刚刚迁入该Host的，那么该Host只用承担该VM 10%的资源请求量
        if (getVmsMigratingIn().contains(vmUid)) {
            // the destination host only experience 10% of the migrating VM's MIPS
            //totalRequestedMips *= 0.1;
            totalRequestedMips = Math.ceil(totalRequestedMips*0.1);//修改点
        }

        //这里Host实际分配给Vm各vCPU的MIPs数的数组构建又分为两种情况
        //情况1：Host剩余的可用MIPs数多于Vm请求的总MIPs数
        if (getAvailableMips() >= totalRequestedMips) {
            List<Double> mipsShareAllocated = new ArrayList<Double>();
            for (Double mipsRequested : mipsShareRequestedCapped) {
                //这里分为三种情况：
                // 1、如果是稳定在该服务器上运行的VM，那么直接分配VM资源请求量给VM
                // 2、如果该VM在这个时间片刚刚迁入，那么只需要分配10%的资源请求量给VM
                // 3、如果该VM在这个时间片要迁出，那么只需要分配90%的资源请求量给VM
                //当然我们在VM放置策略的时候不需要考虑这一点，因为我们希望VM迁移后能够稳定地在Host上运行
                if (getVmsMigratingOut().contains(vmUid)) {
                    // performance degradation due to migration = 10% MIPS
                    mipsRequested *= 0.9;
                } else if (getVmsMigratingIn().contains(vmUid)) {
                    // the destination host only experience 10% of the migrating VM's MIPS
                    mipsRequested *= 0.1;
                }
                mipsShareAllocated.add(Math.floor(mipsRequested));//修改点：添加Math.floor
            }

            getMipsMap().put(vmUid, mipsShareAllocated);
            setAvailableMips(getAvailableMips() - totalRequestedMips);
            //System.out.println("目前剩余的MIPs数为："+getAvailableMips());
        } else {
            //System.out.println("VM #" + vmUid + "分配到Host #" + Host_Belongs + "导致MIPs超分了！！！！！");
            redistributeMipsDueToOverSubscription();
        }

        return true;
    }

    /**
     * Recalculates distribution of MIPs among VMs, considering eventual shortage of MIPS
     * compared to the amount requested by VMs.
     */
    //这个函数是在allocatePesForVm中调用，当Vm的总MIPs请求量大于Host剩余的MIPs时要执行这个函数
    //重新给mipsMap进行赋值（即重新定义应该给各Vm的vCPU分配多少MIPs）
    //updatePe
    protected void redistributeMipsDueToOverSubscription() {
        // First, we calculate the scaling factor - the MIPS allocation for all VMs will be scaled
        // proportionally
        double totalRequiredMipsByAllVms = 0;

        //Debug变量
		double totalMipsAllocated=0;

        Map<String, List<Double>> mipsMapCapped = new HashMap<String, List<Double>>();
        for (Entry<String, List<Double>> entry : getMipsMapRequested().entrySet()) {//逐个遍历该Host的Vm各vCPU的MIPs请求量

            double requiredMipsByThisVm = 0.0;
            String vmId = entry.getKey();
            List<Double> mipsShareRequested = entry.getValue();
            List<Double> mipsShareRequestedCapped = new ArrayList<Double>();
            double peMips = getPeCapacity();
            for (Double mips : mipsShareRequested) {
                if (mips > peMips) {
                    System.out.println("我的算法不可能出现这种情况！！！！！");
                    System.exit(1);//修改点
                    mipsShareRequestedCapped.add(peMips);
                    requiredMipsByThisVm += peMips;
                } else {
                    mipsShareRequestedCapped.add(mips);
                    requiredMipsByThisVm += mips;
                }
            }

            mipsMapCapped.put(vmId, mipsShareRequestedCapped);

            if (getVmsMigratingIn().contains(entry.getKey())) {
                // the destination host only experience 10% of the migrating VM's MIPS
                requiredMipsByThisVm *= 0.1;
            }

            totalRequiredMipsByAllVms += requiredMipsByThisVm;
        }

        double totalAvailableMips = PeList.getTotalMips(getPeList());
        //修改点
        double scalingFactor = totalAvailableMips / totalRequiredMipsByAllVms ;//资源欠缺的比例，所有Vm的MIPs实际获得量等比缩减

        //接下来就是获取Host实际分配给Vm各vCPU的MIPs数了，并重新构造
        // Clear the old MIPS allocation
        getMipsMap().clear();

        // Update the actual MIPS allocated to the VMs
        for (Entry<String, List<Double>> entry : mipsMapCapped.entrySet()) {
            String vmUid = entry.getKey();
            List<Double> requestedMips = entry.getValue();

            List<Double> updatedMipsAllocation = new ArrayList<Double>();
            for (Double mips : requestedMips) {
                if (getVmsMigratingOut().contains(vmUid)) {
                    // the original amount is scaled
                    mips *= scalingFactor;
                    // performance degradation due to migration = 90% MIPS
                    mips *= 0.9;
                } else if (getVmsMigratingIn().contains(vmUid)) {
                    // the original amount is scaled
                    mips *= scalingFactor;
                    // the destination host only experiences 10% of the migrating VM's MIPS
                    mips *= 0.1;
                } else {
                    mips *= scalingFactor;
                }
                totalMipsAllocated+=Math.floor(mips);
                updatedMipsAllocation.add(Math.floor(mips));
            }

            // add in the new map
            getMipsMap().put(vmUid, updatedMipsAllocation);
        }

        //System.out.println("缩减以后的MIPs是：totalMipsAllocated="+totalMipsAllocated);
        //System.out.println("Host的Mips总容量为：getTotalMips="+PeList.getTotalMips(getPeList()));

        // As the host is oversubscribed, there no more available MIPS
        setAvailableMips(0);
    }

}
