/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static org.fmi.aq.enfuser.ftools.FastMath.erfc;

/**
 *This class is for the Gaussian Plume solution computations, taking into account
 * inversion layer height, stability and settling velocity (if applicable)
 * 
 * All the formulas need a specific value of x, r, r_y, r_z. however these
 * are not specifically used in the function but rather precomputed arrays
 * (r_c) are given instead. See the TabledSigma class for more information.
 * 
 * The computations have been organized into 'blocks' according to the
 * documentation (B and Y blocks, the e1 Ermak block etc.)
 * 
 * This class also supports the creation of testing mvisualization with the
 * GaussianTester.
 * 
 * @author johanssl
 */
public class PlumeMath {

    public final static float K = 1;//for the Ermak solution.

    /**
     * Return the B-block for the full Ermak solution.
     * @param r_c the precomputed r-values (for a specific x-value and stability)
     * @param z measuring height above ground [m]
     * @param H emission source height [m]
     * @param w0 the deposition velocity
     * @param wset the settling velocity
     * @return the block value.
     */
    public static float getErmakPlume_b(float[] r_c, float z,
            float H, float w0, float wset) {

        float r_sq = r_c[TabledGaussians.IND_sqrt_r];
        float rz = r_c[TabledGaussians.IND_rz];

        double pre_b = (wset * (z - H) / (-2 * K) - wset * wset * rz / (4 * K * K));//this goes into exp()
        pre_b = FM.fexp(pre_b);

        double be_neg = (z - H) * (z - H) / -4 / rz;
        double be_pos = (z + H) * (z + H) / -4 / rz;
        double b = FM.fexp(be_neg) + FM.fexp(be_pos);

        double a = 2 * w0 * r_c[TabledGaussians.IND_sqrt_pir] / K;

        double pre_erfc_exp = w0 * (z + H) / K + w0 * w0 * rz / K / K;
        pre_erfc_exp = FM.fexp(pre_erfc_exp);

        double toErfc = (z + H) / (2 * r_sq) + w0 * r_sq / K;
        double erfc = erfc(toErfc);

        double product = pre_b * (b - a * pre_erfc_exp * erfc);

        return (float) product;
    }
    
    /**
     * Return the B-block for the basic Gaussian Plume solution (no inversion)
     * @param r_c the precomputed r-values (for a specific x-value and stability)
     * @param z measuring height above ground [m]
     * @param H emission source height [m]
     * @return  the block value
     */
    public static float getPlume_b(float[] r_c, float z, float H) {
        float rz = r_c[TabledGaussians.IND_rz];
        double be_neg = (z - H) * (z - H) / -4 / rz;
        double be_pos = (z + H) * (z + H) / -4 / rz;
        double b = FM.fexp(be_neg) + FM.fexp(be_pos);
        return (float) b;
    }

        /**
     * Return the B-block for the Gaussian Plume solution WITH inversion layer height.
     * However, this can also be used for Gaussian Puff as well with slight
     * modifications.
     * 
     * @param N amount of terms assessed in the infinite series
     * @param r_c the precomputed r-values (for a specific x-value and stability)
     * @param z measuring height above ground [m]
     * @param D the inversion layer height [m]
     * @param H emission source height [m]
     * @param forPuff if true then the assessment is done for Gaussian Puff
     * that also needs this block. If true, then the normalization coefficient
     * is slightly different.
     * @return  the block value
     */
    public static float getInversion_b(int N, float z, float H, float D,
            float[] r_c, boolean forPuff) {

        if (z > D || H > D) {
            return 0;
        }

        float sum = 0.5f;
        // build up the infinite sum. Or not so infinite in this approximation.
        double pizd = PI * z / D;
        double pid2 = (PI / D) * (PI / D);
        double pihd = PI * H / D;

        for (int n = 1; n <= N; n++) {
            sum += fcos(n * pizd) * fcos(n * pihd) * FM.fexp(-r_c[TabledGaussians.IND_rz] * n * n * pid2);
        }
        if (forPuff) {
            sum *= r_c[TabledGaussians.IND_PUFF_CORRECTION];//for puff there is a need for another normalizing factor when b is subsituted with b_il
        }
        return sum / D;
    }
    
            /**
     * Return the B-block for the Gaussian Plume solution WITH inversion layer height.
     * However, this can also be used for Gaussian Puff as well with slight
     * modifications.
     * 
     * @param N amount of terms assessed in the infinite series
     * @param r_c the precomputed r-values (for a specific x-value and stability)
     * @param z measuring height above ground [m]
     * @param D the inversion layer height [m]
     * @param H emission source height [m]
     * @param forPuff if true then the assessment is done for Gaussian Puff
     * that also needs this block. If true, then the normalization coefficient
     * is slightly different.
     * @return  the block value
     */
    public static float getInversion_b_slow(int N, float z, float H, float D,
            float[] r_c, boolean forPuff) {

        if (z > D || H > D) {
            return 0;
        }

        float sum = 0.5f;
        // build up the infinite sum. Or not so infinite in this approximation.
        double pizd = PI * z / D;
        double pid2 = (PI / D) * (PI / D);
        double pihd = PI * H / D;

        for (int n = 1; n <= N; n++) {
            sum += cos(n * pizd) * cos(n * pihd) * Math.exp(-r_c[TabledGaussians.IND_rz] * n * n * pid2);
        }
        if (forPuff) {
            sum *= r_c[TabledGaussians.IND_PUFF_CORRECTION];//for puff there is a need for another normalizing factor when b is subsituted with b_il
        }
        return sum / D;
    }

    /**
     * Get the Y-block value that is used for all kinds of Gaussian
     * formulas in a similar fashion.
     * @param r_c the precomputed r-values (for a specific x-value and stability)
     * @param y the distance from the x-axis [m]
     * @return Y-block value.
     */
    public static float getYblock(float[] r_c, float y) {
        double ex = -y * y / (4 * r_c[TabledGaussians.IND_ry]);
        return FM.fexp(ex);
    }

    public final static int N_BIL = 300;
    public final static float BLH_PENETR = 0f;
    public final static float XD_RATIO_MAX = 3f;
    public final static float XD_RATIO_MIN = 2f;

    public static float getSettlingComp(float[] r_c, float z, float H, float setw) {
        double pre_b = (WSET * (z - H) / (-2 * K) - WSET * WSET * r_c[TabledGaussians.IND_rz] / (4 * K * K));//this goes into exp()
        return FM.fexp(pre_b);//this cannot use fastNegativeExp() since it can be positive
    }
    
    /**
     * A fixed value for deposition velocity for the Ermak solution, aimed for
     * coarse particles.
     */
    public final static float W0 = 5e-5f;
    /**
     * A fixed value for settling velocity for the Ermak solution, aimed for
     * coarse particles.
     */
    public final static float WSET = 0.02f;

    private static double[] init() {
        double[] cosPrecomp = new double[CPN + 1];
        for (int i = 0; i < CPN + 1; i++) {
            double phi = (float) i / CPN * DIV;
            cosPrecomp[i] = cos(phi);
        }
        return cosPrecomp;
    }

    private final static double DIV = 2 * Math.PI;
    private final static int CPN = 20000;
    private final static double[] cosPrecomp = init();

    /**
     * This function is used to fetch a precomputed value for cos(x), intended
     * to be used for the B_IL block computations.
     * The 2*PI range for the function has been represented with CPN amount
     * of unique values (20000).
     * @param val the value x for cos(x).
     * @return the precomputed cosine value.
     */
    public static double fcos(double val) {
        if (val > DIV) {
            val -= (int) (val / DIV) * DIV;
        }
        return cosPrecomp[(int) (CPN * val / DIV)];
    }

}
