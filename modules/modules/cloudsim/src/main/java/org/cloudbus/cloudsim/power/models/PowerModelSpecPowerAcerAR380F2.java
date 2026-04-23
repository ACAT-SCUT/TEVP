package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerAcerAR380F2 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 81.9, 107, 117, 129, 146, 165, 184, 200, 212, 239, 254};
    private final double[] peff = { 0, 932, 1701, 2306, 2730, 3000, 3241, 3482, 3772, 3737, 3904};
    private final double PeakPower = 254;//峰值能耗
    private final double PeakPeff = 3904;//峰值效能比
    private final double pp_CPU_util = 1.0;//峰值效能比下的CPU利用率

    @Override
    protected double getPowerData(int index) {return power[index];}

    @Override
    protected double getPeffData(int index) {return peff[index];}

    @Override
    public final double[] getPowerList() {return power;}

    @Override
    public final double[] getPeffList() {return peff;}

    @Override
    public double getPp_CPU_tuil(){return pp_CPU_util;}

    @Override
    public double getPeakPower() {return PeakPower;}

    @Override
    public double getPeakPeff() {return PeakPeff;}
}

