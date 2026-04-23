package org.cloudbus.cloudsim.power;

import java.util.ArrayList;

/**
 * DDP算法专属的Vm结构
 * <p>
 * PS: Vm所需的物理内核Pe的数量和vCPU的数量有一定的关联，主要看运营商想不想把vCPU分在不同的Pe内，
 * CloudSim中没有考虑这一点，仅仅是我的算法在调度时会检查这项属性而已，实际上CloudSim还是会
 * 按照它自己的一套来分配vCPU，不一定把vCPU分配到不同的Pe上，详见VmSchedulerTimeShared。我
 * 的调度算法也仅仅是检查了所需物理内核Pe的数量是否满足Vm的需求，真正在分配vCPU的时候还是得
 * 按照CloudSim那一套来做，毕竟两边vCPU分配策略不一致的话算法跑不起来
 */
public class DDPVm {
    //静态数据
    public int VmId;
    public double MipsCapacityOfSingleVcpu;//VM单个vCPU的MIPs上限

    public ArrayList<Double> MIPs_Request = new ArrayList<>();//这个才是vCPU实时的MIPs请求量，设定好了之后就不会变化
    public double Total_CPU_Request = 0;//Vm向host申请的包括各个内核在内的CPU资源量（VM放置检测都用该值）
    public double RAM_Request;//Vm向host申请的RAM资源量（VM放置时检测都用该值）
    public double BW_Request;//Vm向host申请的带宽资源量（VM放置时检测都用该值）

    public double Total_CPU_Cap;//特定Vm类型的MIPs容量<即所有VCpu的MIPs容量相加>（暂时用不上）
    public double RAM_Cap;//特定Vm类型的Ram容量（暂时用不上）
    public double BW_Cap;//特定Vm类型的Bandwidth容量（暂时用不上）
    public double MigraRatio;//仅在待迁移Vm选择时需要用到该值（无需初始化）

    //动态数据<每次Vm放置后都有修改的值>
    public int Host_Belongs;//该VM归属于哪台Host（其实仅仅是一个HostId，Vm的操作不会更改Host_Belongs里面的东西）
    //该VM的vCPU被放置到Host的哪些Pe里面，且包括每个Pe的MIPs请求量
    //（这数据结构主要是“选择待迁移Vm并从源Host处remove”时要用到，毕竟迁移的时候要维护Host各Pe的MIPs余量）
    //（每个DDPVmCoreBelongs并不代表一个vCPU，仅表示Pe上分配了该Vm的这么一些MIPs，
    // 这和CloudSim的PeProvisioner的MIPs分配机制有关，比如vCPU数量为4，那么DDPVmCoreBelongs的数量可能为5，
    // 因为其中一个vCPU可能被掰开两半，放在了Host的两个物理核里面，所以有可能2个DDPVmCoreBelongs都是1个vCPU申请到的）
    public ArrayList<DDPPeMipsMapPair> PeMipsMap = new ArrayList<>();
    public double Total_CPU_Allocate = 0;//实际从host得到的CPU资源量（主要是超载Host处理中对Vm进行迁出时使用，后续调度中其值都等于request的值）
    public double RAM_Allocate;//实际从host得到的RAM资源量（主要是超载Host处理中对Vm进行迁出时使用，后续调度中其值都等于request的值）
    public double BW_Allocate;//实际从host得到的带宽资源量（主要是超载Host处理中对Vm进行迁出时使用，后续调度中其值都等于request的值）

    //构造函数
    public DDPVm(int VmId, int Host_Belongs, double MipsCapacityOfSingleVcpu,
                 ArrayList<DDPPeMipsMapPair> PeMipsMap, ArrayList<Double> MIPs_Request,
                 double RAM_Request, double BW_Request, double RAM_Cap, double BW_Cap,
                 double RAM_Allocate, double BW_Allocate) {
        //静态数据
        this.VmId = VmId;
        this.MipsCapacityOfSingleVcpu = MipsCapacityOfSingleVcpu;

        this.MIPs_Request = MIPs_Request;//这里浅复制没问题，反正后面不会修改该值
        for (Double val : MIPs_Request) {
            this.Total_CPU_Request += val;
        }
        this.RAM_Request = RAM_Request;
        this.BW_Request = BW_Request;

        this.Total_CPU_Cap = MIPs_Request.size() * MipsCapacityOfSingleVcpu;
        this.RAM_Cap = RAM_Cap;
        this.BW_Cap = BW_Cap;

        //动态数据
        this.Host_Belongs = Host_Belongs;
        for (DDPPeMipsMapPair cb : PeMipsMap) {//保险起见，用深拷贝
            DDPPeMipsMapPair tmpCb = new DDPPeMipsMapPair(cb.PeId, cb.PeMipsReq);
            this.PeMipsMap.add(tmpCb);
        }
        //Total_CPU_Req的计算方法是各核的MIPs数相加
        for (DDPPeMipsMapPair cb : PeMipsMap) {
            Total_CPU_Allocate += cb.PeMipsReq;
        }
        this.RAM_Allocate = RAM_Allocate;
        this.BW_Allocate = BW_Allocate;
    }

    //复制构造函数（深拷贝）
    public DDPVm(DDPVm a) {
        //静态数据
        this.VmId = a.VmId;
        this.MipsCapacityOfSingleVcpu = a.MipsCapacityOfSingleVcpu;

        this.MIPs_Request = a.MIPs_Request;
        this.Total_CPU_Request =a.Total_CPU_Request;
        this.RAM_Request=a.RAM_Request;
        this.BW_Request =a.BW_Request;

        this.Total_CPU_Cap =a.Total_CPU_Cap;
        this.RAM_Cap=a.RAM_Cap;
        this.BW_Cap=a.BW_Cap;

        //动态数据
        this.Host_Belongs = a.Host_Belongs;
        for (DDPPeMipsMapPair pair : a.PeMipsMap) {//深拷贝
            DDPPeMipsMapPair tmpPair = new DDPPeMipsMapPair(pair.PeId, pair.PeMipsReq);
            this.PeMipsMap.add(tmpPair);
        }
        this.Total_CPU_Allocate = a.Total_CPU_Allocate;
        this.RAM_Allocate = a.RAM_Allocate;
        this.BW_Allocate = a.BW_Allocate;
    }

    //复制函数（只复制动态数据即可，用于整个DEPSO Vm放置流程后的更新）
    public void CopyUpdate(DDPVm vm) {
        this.Host_Belongs = vm.Host_Belongs;//CloudSim只需要获取更新后的这个属性，其它的不管
        this.PeMipsMap = vm.PeMipsMap;//这里浅拷贝没问题，因为外部传进来的Core_Belongs不会再改
        this.Total_CPU_Allocate = vm.Total_CPU_Allocate;
        this.RAM_Allocate = vm.RAM_Allocate;
        this.BW_Allocate = vm.BW_Allocate;
    }

    //VM放置更新函数
    public void placementUpdate(int Host_Belongs, ArrayList<DDPPeMipsMapPair> PeMipsMap,
                                double Total_CPU_Allocate, double RAM_Allocate, double Band_Allocate) {
        this.Host_Belongs = Host_Belongs;
        this.PeMipsMap = PeMipsMap;//浅拷贝就好了，因为<CheckVmPlacement>放置过程中构造的PeMipsMap不会在别处改变
        this.Total_CPU_Allocate = Total_CPU_Allocate;
        this.RAM_Allocate = RAM_Allocate;
        this.BW_Allocate = Band_Allocate;
    }

}
