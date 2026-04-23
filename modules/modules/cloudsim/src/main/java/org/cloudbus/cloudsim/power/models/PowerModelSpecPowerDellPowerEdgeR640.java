package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerDellPowerEdgeR640 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 55, 125, 151, 176, 202, 232, 261, 301, 357, 421, 469};
    private final double[] peff = { 0, 4614, 7651, 9850, 11429, 12454, 13271, 13420, 12926, 12352, 12284};
    private final double PeakPower = 469;//峰值能耗
    private final double PeakPeff = 13420;//峰值效能比
    private final double pp_CPU_util = 0.7;//峰值效能比下的CPU利用率

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
