package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerHuaweiFusionServer5288V5 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 51.5, 119, 142, 165, 188, 194, 236, 256, 283, 336, 422};
    private final double[] peff = { 0, 5156, 8605, 11111, 13061, 15728, 15518, 16712, 17285, 16499, 14479};
    private final double PeakPower = 422;//峰值能耗
    private final double PeakPeff = 17285;//峰值效能比
    private final double pp_CPU_util = 0.8;//峰值效能比下的CPU利用率

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

