package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerHuaweiFusionServer2288HV5 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 49.6, 120, 142, 162, 182, 201, 233, 277, 313, 381, 461};
    private final double[] peff = { 0, 4957, 8375, 10985, 13065, 14739, 15362, 15029, 15181, 14083, 12882};
    private final double PeakPower = 461;//峰值能耗
    private final double PeakPeff = 15362;//峰值效能比
    private final double pp_CPU_util = 0.6;//峰值效能比下的CPU利用率

    @Override
    protected double getPowerData(int index) {
        return power[index];
    }

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
