/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

/**
 * A Host is a Physical Machine (PM) inside a Datacenter. It is also called as a Server.
 * It executes actions related to management of virtual machines (e.g., creation and destruction).
 * A host has a defined policy for provisioning memory and bw, as well as an allocation policy for
 * Pe's to virtual machines. A host is associated to a datacenter. It can host virtual machines.
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
//一个Host的各种信息，VM放置迁移操作的数据更新函数也在这里，我们能耗仿真中最重要的一个类之一
//PS：不过VM放置迁移策略并不在这里，这个类在每个时间片VM放置迁移策略输出VM-Host映射关系后，按照新的VM-Host的映射关系更新数据
//（具体的VM放置迁移策略在Datacenter里面，封装成VmAllocationPolicy的子类）
public class Host {

    /**
     * The id of the host.
     */
    private int id;

    /**
     * The storage capacity.
     */
    private long storage;

    /**
     * The ram provisioner.
     */
    private RamProvisioner ramProvisioner;

    /**
     * The bw provisioner.
     */
    private BwProvisioner bwProvisioner;

    /**
     * The allocation policy for scheduling VM execution.
     */
    private VmScheduler vmScheduler;

    /**
     * The list of VMs assigned to the host.
     */
    private final List<? extends Vm> vmList = new ArrayList<Vm>();

    /**
     * The Processing Elements (PEs) of the host, that
     * represent the CPU cores of it, and thus, its processing capacity.
     */
    private List<? extends Pe> peList;

    /**
     * Tells whether this host is working properly or has failed.
     */
    private boolean failed;

    /**
     * The VMs migrating in.
     */
    private final List<Vm> vmsMigratingIn = new ArrayList<Vm>();

    /**
     * The datacenter where the host is placed.
     */
    private Datacenter datacenter;

    /**
     * Instantiates a new host.
     *
     * @param id             the host id
     * @param ramProvisioner the ram provisioner
     * @param bwProvisioner  the bw provisioner
     * @param storage        the storage capacity
     * @param peList         the host's PEs list
     * @param vmScheduler    the vm scheduler
     */
    public Host(
            int id,
            RamProvisioner ramProvisioner,
            BwProvisioner bwProvisioner,
            long storage,
            List<? extends Pe> peList,
            VmScheduler vmScheduler) {
        setId(id);
        setRamProvisioner(ramProvisioner);
        setBwProvisioner(bwProvisioner);
        setStorage(storage);
        setVmScheduler(vmScheduler);

        setPeList(peList);
        setFailed(false);
    }

    /**
     * Requests updating of cloudlets' processing in VMs running in this host.
     *
     * @param currentTime the current time
     * @return expected time of completion of the next cloudlet in all VMs in this host or
     * {@link Double#MAX_VALUE} if there is no future events expected in this host
     * @pre currentTime >= 0.0
     * @post $none
     * @todo there is an inconsistency between the return value of this method
     * and the individual call of {@link Vm #updateVmProcessing(double, java.util.List),
     * and consequently the {@link CloudletScheduler #updateVmProcessing(double, java.util.List)}.
     * The current method returns {@link Double #MAX_VALUE}  while the other ones
     * return 0. It has to be checked if there is a reason for this
     * difference.}
     */
    //更新该主机上VM的Cloudlet处理请求的结果
    public double updateVmsProcessing(double currentTime) {
        double smallerTime = Double.MAX_VALUE;

        for (Vm vm : getVmList()) {
            double time = vm.updateVmProcessing(
                    currentTime, getVmScheduler().getAllocatedMipsForVm(vm));//Host给特定Vm实际分配的Mips作为参数
            if (time > 0.0 && time < smallerTime) {
                smallerTime = time;
            }
        }

        return smallerTime;
    }

    /**
     * Adds a VM migrating into the current (dstHost) host.
     *
     * @param vm the vm
     */
    public void addMigratingInVm(Vm vm) {
        vm.setInMigration(true);
        if (!getVmsMigratingIn().contains(vm)) {
            if (getStorage() < vm.getSize()) {
                Log.printConcatLine("[VmScheduler.addMigratingInVm] Allocation of VM #", vm.getId(), " to Host #",
                        getId(), " failed by storage");
                System.exit(0);
            }

            if (!getRamProvisioner().allocateRamForVm(vm, vm.getCurrentRequestedRam())) {
                Log.printConcatLine("[VmScheduler.addMigratingInVm] Allocation of VM #", vm.getId(), " to Host #",
                        getId(), " failed by RAM");
                System.exit(0);
            }

            if (!getBwProvisioner().allocateBwForVm(vm, vm.getCurrentRequestedBw())) {
                Log.printLine("[VmScheduler.addMigratingInVm] Allocation of VM #" + vm.getId() + " to Host #"
                        + getId() + " failed by BW");
                System.exit(0);
            }
            getVmScheduler().getVmsMigratingIn().add(vm.getUid());
            //System.out.println("[VmScheduler.addMigratingInVm]：Host #"+getId()+" 正在对迁移的Vm #"+vm.getId()+" 进行放置");
            if (!getVmScheduler().allocatePesForVm(vm, vm.getCurrentRequestedMips())) {
                Log.printLine("[VmScheduler.addMigratingInVm] Allocation of VM #" + vm.getId() + " to Host #"
                        + getId() + " failed by MIPS");
                System.exit(0);
            }

            setStorage(getStorage() - vm.getSize());
            getVmsMigratingIn().add(vm);
            getVmList().add(vm);
            //这里并不是更新两次，而是把目标Host和源Host的Vm数据都更新一下
            updateVmsProcessing(CloudSim.clock());
            vm.getHost().updateVmsProcessing(CloudSim.clock());
        }
    }

    /**
     * Removes a migrating in vm.
     *
     * @param vm the vm
     */
    public void removeMigratingInVm(Vm vm) {
        vmDeallocate(vm);
        getVmsMigratingIn().remove(vm);//这是Host的VmsMigratingIn()
        getVmList().remove(vm);
        getVmScheduler().getVmsMigratingIn().remove(vm.getUid());
        vm.setInMigration(false);
    }

    /**
     * Reallocate migrating in vms. Gets the VM in the migrating in queue
     * and allocate them on the host.
     */
    //这个函数只在CloudSim的VM调度算法中使用，我用不上
    public void reallocateMigratingInVms() {
        for (Vm vm : getVmsMigratingIn()) {
            if (!getVmList().contains(vm)) {
                getVmList().add(vm);
            }
            if (!getVmScheduler().getVmsMigratingIn().contains(vm.getUid())) {
                getVmScheduler().getVmsMigratingIn().add(vm.getUid());
            }
            getRamProvisioner().allocateRamForVm(vm, vm.getCurrentRequestedRam());
            getBwProvisioner().allocateBwForVm(vm, vm.getCurrentRequestedBw());
            getVmScheduler().allocatePesForVm(vm, vm.getCurrentRequestedMips());
            setStorage(getStorage() - vm.getSize());
        }
    }

    /**
     * Checks if the host is suitable for vm. If it has enough resources
     * to attend the VM.
     *
     * @param vm the vm
     * @return true, if is suitable for vm
     */
    // 这个函数其实并不合理，归根结底还是CloudSim的vCPU分配机制不合理（将vCPU掰开两半放置的这种机制）
    // 现实中的特定类型的VM都是在特定类型的Host上放置的，根本不需要判断第一二句，但是我们现在仿真场景
    // 是所有VM类型可以放置在所有Host类型上面
    // 另外，这个函数我的算法里面完全没有用到，因为我用的是自己搭建的Host、Vm数据结构
    public boolean isSuitableForVm(Vm vm) {
        return (getVmScheduler().getPeCapacity() >= vm.getMips()//新增限制，Host的Pe容量大于Vm单个vCPU的MIPs容量
        		&& getVmScheduler().getPeCapacity() >= vm.getCurrentRequestedMaxMips()//若没第一句的话这一句是有用的，否则这一句无效
                //&& getNumberOfPes() >= vm.getNumberOfPes()//新增限制，Host的Pe数目对Vm的vCPU数目的限制
                && getVmScheduler().getAvailableMips() >= vm.getCurrentRequestedTotalMips()
                && getRamProvisioner().isSuitableForVm(vm, vm.getCurrentRequestedRam())
                && getBwProvisioner().isSuitableForVm(vm, vm.getCurrentRequestedBw()));
    }

    /**
     * Try to allocate resources to a new VM in the Host.
     *
     * @param vm Vm being started
     * @return $true if the VM could be started in the host; $false otherwise
     * @pre $none
     * @post $none
     */
    public boolean vmCreate(Vm vm) {
        if (getStorage() < vm.getSize()) {
            System.out.println( "getStorage() :"+getStorage() +"vm.getSize():"+vm.getSize());
            Log.printConcatLine("[VmScheduler.vmCreate] Allocation of VM #", vm.getId(), " to Host #", getId(), " failed by storage");
            System.out.println( "[VmScheduler.vmCreate] Allocation of VM #"+vm.getId()+" to Host #"+getId()+ " failed by storage");
            return false;
        }

        if (!getRamProvisioner().allocateRamForVm(vm, vm.getCurrentRequestedRam())) {
            Log.printConcatLine("[VmScheduler.vmCreate] Allocation of VM #", vm.getId(), " to Host #", getId(), " failed by RAM");
            System.out.println( "[VmScheduler.vmCreate] Allocation of VM #"+vm.getId()+" to Host #"+getId()+ " failed by RAM");
            return false;
        }

        if (!getBwProvisioner().allocateBwForVm(vm, vm.getCurrentRequestedBw())) {
            Log.printConcatLine("[VmScheduler.vmCreate] Allocation of VM #", vm.getId(), " to Host #", getId(), " failed by BW");
            System.out.println( "[VmScheduler.vmCreate] Allocation of VM #"+vm.getId()+" to Host #"+getId()+ " failed by BW");
            getRamProvisioner().deallocateRamForVm(vm);
            return false;
        }

        //System.out.println("[VmCreate]：正在将Vm #"+vm.getId()+"放置到 Host #"+getId());
        if (!getVmScheduler().allocatePesForVm(vm, vm.getCurrentRequestedMips())) {
            Log.printConcatLine("[VmScheduler.vmCreate] Allocation of VM #", vm.getId(), " to Host #", getId(), " failed by MIPS");
            System.out.println( "[VmScheduler.vmCreate] Allocation of VM #"+vm.getId()+" to Host #"+getId()+ " failed by MIPS");
            getRamProvisioner().deallocateRamForVm(vm);
            getBwProvisioner().deallocateBwForVm(vm);
            return false;
        }

        setStorage(getStorage() - vm.getSize());
        getVmList().add(vm);
        vm.setHost(this);
        return true;
    }

    /**
     * Destroys a VM running in the host.
     *
     * @param vm the VM
     * @pre $none
     * @post $none
     */
    public void vmDestroy(Vm vm) {
        if (vm != null) {
            vmDeallocate(vm);
            getVmList().remove(vm);
            vm.setHost(null);
            //修改点：把VmMigrationOut维护一下，不然如果VM在其他时间片回迁的时候，CPU_Req的计算会出问题
            getVmScheduler().getVmsMigratingOut().remove(vm.getUid());//修改点
        }
    }

    /**
     * Destroys all VMs running in the host.
     *
     * @pre $none
     * @post $none
     */
    public void vmDestroyAll() {
        vmDeallocateAll();
        for (Vm vm : getVmList()) {
            vm.setHost(null);
            setStorage(getStorage() + vm.getSize());
        }
        getVmList().clear();
    }

    /**
     * Deallocate all resources of a VM.
     *
     * @param vm the VM
     */
    protected void vmDeallocate(Vm vm) {
        getRamProvisioner().deallocateRamForVm(vm);
        getBwProvisioner().deallocateBwForVm(vm);
        getVmScheduler().deallocatePesForVm(vm);
        setStorage(getStorage() + vm.getSize());
    }

    /**
     * Deallocate all resources of all VMs.
     */
    //这函数我用不上
    protected void vmDeallocateAll() {
        getRamProvisioner().deallocateRamForAllVms();
        getBwProvisioner().deallocateBwForAllVms();
        getVmScheduler().deallocatePesForAllVms();
    }

    /**
     * Gets a VM by its id and user.
     *
     * @param vmId   the vm id
     * @param userId ID of VM's owner
     * @return the virtual machine object, $null if not found
     * @pre $none
     * @post $none
     */
    public Vm getVm(int vmId, int userId) {
        for (Vm vm : getVmList()) {
            if (vm.getId() == vmId && vm.getUserId() == userId) {
                return vm;
            }
        }
        return null;
    }

    /**
     * Gets the pes number.
     *
     * @return the pes number
     */
    public int getNumberOfPes() {
        return getPeList().size();
    }

    /**
     * Gets the free pes number.
     *
     * @return the free pes number
     */
    public int getNumberOfFreePes() {
        return PeList.getNumberOfFreePes(getPeList());
    }

    /**
     * Gets the total mips.
     *
     * @return the total mips
     */
    public int getTotalMips() {
        return PeList.getTotalMips(getPeList());
    }

    /**
     * Allocates PEs for a VM.
     *
     * @param vm        the vm
     * @param mipsShare the list of MIPS share to be allocated to the VM
     * @return $true if this policy allows a new VM in the host, $false otherwise
     * @pre $none
     * @post $none
     */
    public boolean allocatePesForVm(Vm vm, List<Double> mipsShare) {
        return getVmScheduler().allocatePesForVm(vm, mipsShare);
    }

    /**
     * Releases PEs allocated to a VM.
     *
     * @param vm the vm
     * @pre $none
     * @post $none
     */
    public void deallocatePesForVm(Vm vm) {
        getVmScheduler().deallocatePesForVm(vm);
    }

    /**
     * Gets the MIPS share of each Pe that is allocated to a given VM.
     *
     * @param vm the vm
     * @return an array containing the amount of MIPS of each pe that is available to the VM
     * @pre $none
     * @post $none
     */
    public List<Double> getAllocatedMipsForVm(Vm vm) {
        return getVmScheduler().getAllocatedMipsForVm(vm);
    }

    /**
     * Gets the total allocated MIPS for a VM along all its PEs.
     *
     * @param vm the vm
     * @return the allocated mips for vm
     */
    public double getTotalAllocatedMipsForVm(Vm vm) {
        return getVmScheduler().getTotalAllocatedMipsForVm(vm);
    }

    /**
     * Returns the maximum available MIPS among all the PEs of the host.
     *
     * @return max mips
     */
    public double getMaxAvailableMips() {
        return getVmScheduler().getMaxAvailableMips();
    }

    /**
     * Gets the total free MIPS available at the host.
     *
     * @return the free mips
     */
    public double getAvailableMips() {
        return getVmScheduler().getAvailableMips();
    }

    /**
     * Gets the host bw.
     *
     * @return the host bw
     * @pre $none
     * @post $result > 0
     */
    public long getBw() {
        return getBwProvisioner().getBw();
    }

    /**
     * Gets the host memory.
     *
     * @return the host memory
     * @pre $none
     * @post $result > 0
     */
    public int getRam() {
        return getRamProvisioner().getRam();
    }

    /**
     * Gets the host storage.
     *
     * @return the host storage
     * @pre $none
     * @post $result >= 0
     */
    public long getStorage() {
        return storage;
    }

    /**
     * Gets the host id.
     *
     * @return the host id
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the host id.
     *
     * @param id the new host id
     */
    protected void setId(int id) {
        this.id = id;
    }

    /**
     * Gets the ram provisioner.
     *
     * @return the ram provisioner
     */
    public RamProvisioner getRamProvisioner() {
        return ramProvisioner;
    }

    /**
     * Sets the ram provisioner.
     *
     * @param ramProvisioner the new ram provisioner
     */
    protected void setRamProvisioner(RamProvisioner ramProvisioner) {
        this.ramProvisioner = ramProvisioner;
    }

    /**
     * Gets the bw provisioner.
     *
     * @return the bw provisioner
     */
    public BwProvisioner getBwProvisioner() {
        return bwProvisioner;
    }

    /**
     * Sets the bw provisioner.
     *
     * @param bwProvisioner the new bw provisioner
     */
    protected void setBwProvisioner(BwProvisioner bwProvisioner) {
        this.bwProvisioner = bwProvisioner;
    }

    /**
     * Gets the VM scheduler.
     *
     * @return the VM scheduler
     */
    public VmScheduler getVmScheduler() {
        return vmScheduler;
    }

    /**
     * Sets the VM scheduler.
     *
     * @param vmScheduler the vm scheduler
     */
    protected void setVmScheduler(VmScheduler vmScheduler) {
        this.vmScheduler = vmScheduler;
    }

    /**
     * Gets the pe list.
     *
     * @param <T> the generic type
     * @return the pe list
     */
    @SuppressWarnings("unchecked")
    public <T extends Pe> List<T> getPeList() {
        return (List<T>) peList;
    }

    /**
     * Sets the pe list.
     *
     * @param <T>    the generic type
     * @param peList the new pe list
     */
    protected <T extends Pe> void setPeList(List<T> peList) {
        this.peList = peList;
    }

    /**
     * Gets the vm list.
     *
     * @param <T> the generic type
     * @return the vm list
     */
    @SuppressWarnings("unchecked")
    public <T extends Vm> List<T> getVmList() {
        return (List<T>) vmList;
    }

    /**
     * Sets the storage.
     *
     * @param storage the new storage
     */
    protected void setStorage(long storage) {
        this.storage = storage;
    }

    /**
     * Checks if the host PEs have failed.
     *
     * @return true, if the host PEs have failed; false otherwise
     */
    public boolean isFailed() {
        return failed;
    }

    /**
     * Sets the PEs of the host to a FAILED status. NOTE: <tt>resName</tt> is used for debugging
     * purposes, which is <b>ON</b> by default. Use {@link #setFailed(boolean)} if you do not want
     * this information.
     *
     * @param resName the name of the resource
     * @param failed  the failed
     * @return <tt>true</tt> if successful, <tt>false</tt> otherwise
     */
    public boolean setFailed(String resName, boolean failed) {
        // all the PEs are failed (or recovered, depending on fail)
        this.failed = failed;
        PeList.setStatusFailed(getPeList(), resName, getId(), failed);
        return true;
    }

    /**
     * Sets the PEs of the host to a FAILED status.
     *
     * @param failed the failed
     * @return <tt>true</tt> if successful, <tt>false</tt> otherwise
     */
    public boolean setFailed(boolean failed) {
        // all the PEs are failed (or recovered, depending on fail)
        this.failed = failed;
        PeList.setStatusFailed(getPeList(), failed);
        return true;
    }

    /**
     * Sets the particular Pe status on the host.
     *
     * @param peId   the pe id
     * @param status Pe status, either <tt>Pe.FREE</tt> or <tt>Pe.BUSY</tt>
     * @return <tt>true</tt> if the Pe status has changed, <tt>false</tt> otherwise (Pe id might not
     * be exist)
     * @pre peID >= 0
     * @post $none
     */
    public boolean setPeStatus(int peId, int status) {
        return PeList.setPeStatus(getPeList(), peId, status);
    }

    /**
     * Gets the vms migrating in.
     *
     * @return the vms migrating in
     */
    public List<Vm> getVmsMigratingIn() {
        return vmsMigratingIn;
    }

    /**
     * Gets the data center of the host.
     *
     * @return the data center where the host runs
     */
    public Datacenter getDatacenter() {
        return datacenter;
    }

    /**
     * Sets the data center of the host.
     *
     * @param datacenter the data center from this host
     */
    public void setDatacenter(Datacenter datacenter) {
        this.datacenter = datacenter;
    }

}
