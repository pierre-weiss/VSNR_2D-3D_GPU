

// ------------------------------------------------- //
//                                                   //
//             FIJI PLUGIN : VSNR 3D GPU             //
//                                                   //
// ------------------------------------------------- //
// Original algorithm :                              //
//   Jerome FEHRENBACH, Pierre WEISS                 //
// Plugin developers :                               //
//   Pierre WEISS, Morgan GAUTHIER, Jean EYMERIE     //
// ------------------------------------------------- //


import java.net.URL;
import java.io.File;
import java.lang.Math;
import java.lang.ClassLoader;
import java.nio.file.Paths;
import java.nio.FloatBuffer;
import java.awt.Font;
import java.awt.AWTEvent;
import java.awt.TextField;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Vector;
import com.sun.jna.Library;
import com.sun.jna.Native;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.gui.StackWindow;
import ij.gui.DialogListener;
import ij.io.OpenDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.measure.Calibration;


// objectives : denoising 3D Images
public class VSNR_3D implements PlugInFilter {

    private String filterType;
    private ImagePlus image;
    private int inputMethod;
    
    private int slice;
    private int frame;
    private int chan;

    private int sBlock;
    private int dBlock;

    private ArrayList<Float> listFilters = new ArrayList<Float>();

    private float level = 1;

    private float sigmaX = 3;
    private float sigmaY = 1;
    private float sigmaZ = 1;

    private float thetaX = 0;
    private float thetaY = 0;
    private float thetaZ = 0;

    private float beta   = 10;
    private int   nit    = 20;
    private int   nBlock;

    private boolean bLog  = false;

    private VsnrDllLoader dll = null;

    // --------------------------------------------------------------------

    @Override
    public void run(ImageProcessor ip)
    {
        // exit if trying to treate a 2D image
        if (slice == 1) exitWindow("Use VSNR 2D for 2D images !");

        // initialize the dll
        initDll();
        nBlock = dll.getMaxBlocks();

        // check if an image is open
        if (ip.equals(null)) {
            exitWindow("Open an image please !");
        } else {
            if (configuration()) {
                //if (inputMethod == 0) printParams();
                printParams();
                new StackWindow(denoiseCuda3D());
            }
        }
    }

    @Override
    public int setup(String arg, ImagePlus img)
    {
        if (img == null) exitWindow("Open an image please !");
        int[] data  = img.getDimensions();
        this.image  = img;
        this.chan   = data[2];
        this.slice  = data[3];
        this.frame  = data[4];
        this.sBlock = this.slice;
        this.dBlock = 0;
        return DOES_ALL;
    }

    // refactor config operations
    private boolean configuration()
    {
        if (askMethod()) {
            // GUI configuration
            if (inputMethod == 0) {
                if (askFilterEx()) return askNbIterations();
            // text file configuration
            } else {
                return readFile();
            }
        }
        return false;
    }

    // Open an error window with a msg and exit the plugin
    private void exitWindow(String msg)
    {
        IJ.error("Error", msg);
        throw new RuntimeException(Macro.MACRO_CANCELED);
    }

    // asking the methode used to set params : GUI or text file
    private boolean askMethod()
    {
        GenericDialog g = new GenericDialog("Choose the way to use the plugin");

        String authors = "Welcome to VSNR plugin !\n"
                + "In case you use this algorithm, please cite:\n"
                + "Variational algorithms to remove stationary noise. Application to microscopy imaging.\n"
                + "J. Fehrenbach, P. Weiss and C. Lorenzo, IEEE Image Processing Vol. 21, Issue 10, pages 4420 - 4430, October (2012).\n"
                + "Processing stationary noise: model and parameter selection in variational methods.\n"
                + "J. Fehrenbach, P. Weiss, SIAM Journal on Imaging Science, vol. 7, issue 2, (2014). \n"
                + "Plugin Developers : Benjamin Font, Leo Mouly, Morgan Gauthier, Jean Eymerie, Pierre Weiss\n";
        g.addMessage(authors, new Font(authors, Font.CENTER_BASELINE, 13));

        String s = "Where are the parameters from ?";
        String[] tabChoice = {"Graphic User Interface (GUI)", "Text File (.txt)"};
        String defaultItem = "Choose the input type";
        g.addChoice(s, tabChoice, defaultItem);
        g.pack();
        g.showDialog();

        inputMethod = (g.getNextChoice().equals(tabChoice[0]) ? 0 : 1);

        return !(g.wasCanceled());
    }

    // for text file method
    // read text file to setup filter params
    private boolean readFile()
    {
        OpenDialog od = new OpenDialog("Choose the file to read", "");
        String tmp, path = od.getDirectory() + od.getFileName();
        Boolean error = false;
        try {
            Scanner scanFile = new Scanner(new File(path));
            while (scanFile.hasNextLine() && !error) {
                Scanner scanLine = new Scanner(scanFile.nextLine());
                switch (getStringFlag(scanLine.next())) {
                    case 1 :
                        nit = Integer.parseInt(scanLine.next());
                        break;
                    case 2 :
                        tmp = scanLine.next();
                        nBlock = (tmp.equals("auto") ? dll.getMaxBlocks() : Integer.parseInt(tmp));
                        break;
                    case 3 :
                        bLog = Boolean.parseBoolean(scanLine.next());
                        break;
                    case 4 :
                        tmp = scanLine.next();
                        sBlock = (tmp.equals("auto") ? slice : Integer.parseInt(tmp));
                        break;
                    case 5 :
                        tmp = scanLine.next();
                        if (tmp.equals("auto")) dBlock = 0;
                        else dBlock = (sBlock == slice ? 0 : Integer.parseInt(tmp));
                        break;
                    case 6 :
                        filterType = scanLine.next();
                        // if error
                        error = (!(filterType.equals("Dirac")) && !(filterType.equals("Gabor")));
                        break;
                    case 7 :
                        level = Float.parseFloat(scanLine.next());
                        if (filterType.equals("Dirac")) {
                            listFilters.add((float)0);
                            listFilters.add((float)level);
                        }
                        break;
                    case 8 :
                        sigmaX = Float.parseFloat(scanLine.next());
                        break;
                    case 9 :
                        sigmaY = Float.parseFloat(scanLine.next());
                        break;
                    case 10 :
                        sigmaZ = Float.parseFloat(scanLine.next());
                        break;
                    case 11 :
                        thetaX = Float.parseFloat(scanLine.next());
                        break;
                    case 12 :
                        thetaY = Float.parseFloat(scanLine.next());
                        break;
                    case 13 :
                        thetaZ = Float.parseFloat(scanLine.next());
                        if (filterType.equals("Gabor")) {
                            listFilters.add((float)1);
                            listFilters.add((float)level);
                            listFilters.add((float)sigmaX);
                            listFilters.add((float)sigmaY);
                            listFilters.add((float)sigmaZ);
                            listFilters.add((float)thetaX);
                            listFilters.add((float)thetaY);
                            listFilters.add((float)thetaZ);
                        }
                        break;
                    case 0 :
                    default :
                        break;
                }
                scanLine.close();
            }
            scanFile.close();
        } catch (Exception e) {
            e.printStackTrace();
            IJ.log("Error : the text file is not conform !");
            return false;
        }
        if (error) {
            IJ.log("Error : the text file is not conform !");
            return false;
        }
        return true;
    }

    // to avoid switch(String) for Java 1.6 Compatibility
    private int getStringFlag(String str)
    {
        if (str.equals("Iteration_Number:")) return 1;
        else if (str.equals("Num_Block:"))   return 2;
        else if (str.equals("Log:"))         return 3;
        else if (str.equals("sBlock:"))      return 4;
        else if (str.equals("dBlock:"))      return 5;
        else if (str.equals("Filter_Type:")) return 6;
        else if (str.equals("Noise_Level:")) return 7;
        else if (str.equals("sigmaX:"))      return 8;
        else if (str.equals("sigmaY:"))      return 9;
        else if (str.equals("sigmaZ:"))      return 10;
        else if (str.equals("thetaX:"))      return 11;
        else if (str.equals("thetaY:"))      return 12;
        else if (str.equals("thetaZ:"))      return 13;
        else if (str.equals("***"))          return 0;
        else return (-1);
    }

    // print into the log windows the text file to use for text file method
    private void printParams()
    {
        int k = 0;
        IJ.log("#VSNR-3D");
        IJ.log("Iteration_Number: " + nit);
        if (nBlock == dll.getMaxBlocks())
            IJ.log("Num_Block: auto");
        else
            IJ.log("Num_Block: " + nBlock);
        IJ.log("Log: " + bLog);
        if (sBlock == slice) {
            IJ.log("sBlock: auto");
            IJ.log("dBlock: auto");
        } else {
            IJ.log("sBlock: " + sBlock);
            IJ.log("dBlock: " + dBlock);
        }
        IJ.log("***");
        while (k < listFilters.size()) {
            if (listFilters.get(k) == 0) {
                IJ.log("Filter_Type: Dirac");
                IJ.log("Noise_Level: " + listFilters.get(k+1));
                IJ.log("***");
                k += 2;
            } else if (listFilters.get(k) == 1) {
                IJ.log("Filter_Type: Gabor");
                IJ.log("Noise_Level: " + listFilters.get(k+1));
                IJ.log("sigmaX: " + listFilters.get(k+2));
                IJ.log("sigmaY: " + listFilters.get(k+3));
                IJ.log("sigmaZ: " + listFilters.get(k+4));
                IJ.log("thetaX: " + listFilters.get(k+5));
                IJ.log("thetaY: " + listFilters.get(k+6));
                IJ.log("thetaZ: " + listFilters.get(k+7));
                IJ.log("***");
                k += 8;
            } else {
                IJ.log("ERROR !");
                break;
            }
        }
    }

    // for GUI method
    // ask for nit + bLog
    private boolean askNbIterations()
    {
        GenericDialog g = new GenericDialog("Setting the number of iterations.");

        g.addNumericField("Iterations :", nit, 0);
        g.addNumericField("Blocks :", sBlock, 0);
        g.addNumericField("Add :", dBlock, 0);
        g.addCheckbox("Multiplicative noise", false);
        g.pack();
        g.showDialog();

        nit    = (int)(g.getNextNumber());
        sBlock = (int)(g.getNextNumber());
        dBlock = (int)(g.getNextNumber());
        bLog   = g.getNextBoolean();

        return !(g.wasCanceled());
    }

    // Condensed filter parametrization
    private boolean askFilterEx()
    {
        GenericDialog g = new GenericDialog("Parametrization");
        ParamListener pListener = new ParamListener();
        String[] choices = {"Gabor", "Dirac"};
        g.addRadioButtonGroup("Filter type", choices, 1, 2, choices[0]);
        g.addNumericField("Noise level :", level, 2);
        g.addNumericField("Sigma X :", sigmaX, 2);
        g.addNumericField("Sigma Y :", sigmaY, 2);
        g.addNumericField("Sigma Z :", sigmaZ, 2);
        g.addNumericField("Theta X :", thetaX, 2);
        g.addNumericField("Theta Y :", thetaY, 2);
        g.addNumericField("Theta Z :", thetaZ, 2);
        g.enableYesNoCancel("OK","+Filter");
        g.addDialogListener(pListener);
        g.pack();
        g.showDialog();

        filterType = g.getNextRadioButton();
        level  = (float)(g.getNextNumber());
        sigmaX = (float)(g.getNextNumber());
        sigmaY = (float)(g.getNextNumber());
        sigmaZ = (float)(g.getNextNumber());
        thetaX = (float)(g.getNextNumber());
        thetaY = (float)(g.getNextNumber());
        thetaZ = (float)(g.getNextNumber());

        if (g.wasCanceled()) return false;

        if (filterType.equals("Dirac")) {
            listFilters.add(0.0f);
            listFilters.add((float)level);
        } else if (filterType.equals("Gabor")) {
            listFilters.add(1.0f);
            listFilters.add((float)level);
            listFilters.add((float)sigmaX);
            listFilters.add((float)sigmaY);
            listFilters.add((float)sigmaZ);
            listFilters.add((float)thetaX);
            listFilters.add((float)thetaY);
            listFilters.add((float)thetaZ);
        } else {
            exitWindow("Unknow filter type, this error should NEVER happen ...");
        }

        if (g.wasOKed()) return true;

        askFilterEx();

        return true;
    }

    // Denoise the image
    // using the VSNR_2D_FIJI_GPU function called from the attached dll/so
    private ImagePlus denoiseCuda3D()
    {
        // security
        if (listFilters.isEmpty()) {
            IJ.log("Unable to process (no filters set) !");
            IJ.log("Try to add some filters first");
            exitWindow("Error : read logs !");
        }
        if (image == null) {
            IJ.log("Something bad happened, you probably closed the image.");
            IJ.log("Please reload VSNR !");
            exitWindow("Error : read logs !");
        }

        IJ.showProgress(0, slice*chan*frame-1);
        IJ.showStatus("Starting denoising ...");

        ImagePlus tmpImage = image.duplicate();
        ImagePlus result   = image.duplicate();
        result.setTitle("vsnr_" + image.getTitle());

        Image3D input, output;

        int step  = Math.min(sBlock, slice);
        int lStep = step;
        int mod   = slice % step;
        int inc   = Math.max(mod / (slice / step), 1);
        int count = 0;
        int dLeft, dRight, timer = 0;

        FloatBuffer buff = getBuffPsi(listFilters);
        float[] d = getDeltas(image);
        int length = listFilters.size();

        for (int k = 0 ; k < slice ; k += lStep) {

            lStep = (count++ < mod ? step + inc : step);
            
            if (k == 0) {
                dLeft  = 0;
                dRight = 2*dBlock;
            } else if (k+lStep > slice-1) {
                dLeft  = 2*dBlock;
                dRight = 0;
            } else {
                dLeft  = dBlock;
                dRight = dBlock;
            }

            for (int c = 0 ; c < chan ; c++) {

                for (int t = 0 ; t < frame ; t++) {

                    IJ.showStatus("Denoising slices "+(k+1)+"-"+(k+lStep)+"/"+slice+", chan "+(c+1)+"/"+chan+", frame "+(t+1)+"/"+frame);

                    input  = new Image3D(tmpImage, k-dLeft, lStep+dLeft+dRight, c, t, bLog);
                    output = input.denoise(buff, length, nit, beta, nBlock, dll, d[0], d[1], d[2]);

                    output.agregate(result, dLeft, dRight, bLog);

                    timer += lStep;
                    IJ.showProgress(timer, slice*chan*frame-1);

                }

            }

        }

        input    = null;
        output   = null;
        tmpImage = null;

        return result;
    }

    // internal use, init the dll
    private void initDll()
    {
        if (dll == null) {

            URL location = VSNR_3D.class.getProtectionDomain().getCodeSource().getLocation();
            String path  = (new File(location.getFile())).getParentFile().toString();

            if (IJ.isLinux()) path += "/libvsnr3d.so";
            else if (IJ.isWindows()) path += "\\libvsnr3d.dll";
            else exitWindow("Unsuported OS !");

            if (!checkFile(path)) exitWindow("Can not find the dll !\nExpecting :\n" + path);
            dll = (VsnrDllLoader)Native.loadLibrary(path, VsnrDllLoader.class);

        }
    }

    // check if the file specified by path exist and is not a directory
    private Boolean checkFile(String path)
    {
        File f = new File(path);
        return (f.exists() && !(f.isDirectory()));
    }

    // -
    private FloatBuffer getBuffPsi(ArrayList<Float> psis)
    {
        int length = psis.size();
        float[] arrPsis = new float[length];
        for (int i = 0 ; i < length ; i++) 
            arrPsis[i] = psis.get(i);
        return FloatBuffer.wrap(arrPsis);
    }

    //-
    private float[] getDeltas(ImagePlus img)
    {
        float[] res = new float[3];
        Calibration cal = img.getCalibration();
        float tx  = (float)(cal.pixelWidth);
        float ty  = (float)(cal.pixelHeight);
        float tz  = (float)(cal.pixelDepth);
        float min = Math.min(Math.min(tx, ty), tz);
        res[0] = tx / min;
        res[1] = ty / min;
        res[2] = tz / min;
        return res;
    }

    // wraping image manipulation
    private class Image3D {

        private int width;
        private int height;
        private int depth;
        private int chan;
        private int frame;
        private int start;

        private Boolean bColor;

        private float[][] arr;
        private float[]   max;

        public Image3D(ImagePlus img, int start, int size, int channel, int frame, Boolean bLog)
        {
            this.width  = img.getWidth();
            this.height = img.getHeight();
            this.depth  = size;
            this.chan   = channel;
            this.frame  = frame;
            this.start  = start;
            this.bColor = (img.getBitDepth() == 24);

            ImageProcessor ip;
            float tmp;

            if (bColor) {

                this.allocate(3, width*height*depth);

                int[] pixel = new int[3];
                for (int k = 0 ; k < size ; k++) {
                    ip = getIP(img,k);
                    for (int j = 0 ; j < height ; j++) {
                        for (int i = 0 ; i < width ; i++) {
                            ip.getPixel(i,j,pixel);
                            for (int m = 0 ; m < 3 ; m++) {
                                tmp = itof(pixel[m]);
                                if (bLog) tmp = logf(tmp);
                                max[m] = Math.max(max[m], tmp);
                                arr[m][i+width*(j+height*k)] = tmp;
                            }
                        }
                    }
                }

            } else {

                this.allocate(1, width*height*depth);
                
                for (int k = 0 ; k < size ; k++) {
                    ip = getIP(img,k);
                    for (int j = 0 ; j < height ; j++) {
                        for (int i = 0 ; i < width ; i++) {
                            tmp = ip.getPixelValue(i,j)+1.0f;
                            if (bLog) tmp = logf(tmp);
                            max[0] = Math.max(max[0], tmp);
                            arr[0][i+width*(j+height*k)] = tmp;
                        }
                    }
                }

            }
        }

        public Image3D(int width, int height, int depth, int chan, int frame, int start, Boolean bColor)
        {
            this.width  = width;
            this.height = height;
            this.depth  = depth;
            this.bColor = bColor;
            this.chan   = chan;
            this.frame  = frame;
            this.start  = start;

            int dim = (bColor ? 3 : 1);

            this.allocate(dim, width*height*depth);
        }

        private void allocate(int dim, int size)
        {
            try {
                arr = new float[dim][size];
                max = new float[dim];
                Arrays.fill(max, Float.NEGATIVE_INFINITY);
            } catch (Throwable e) {
                e.printStackTrace();
                String str = "Error :\nProbably running out of memory !\nLaunch IJ in command line to get the stack trace ...";
                IJ.log(str);
                exitWindow(str);
            }
        }

        private ImageProcessor getIP(ImagePlus img, int slice)
        {
            img.setPositionWithoutUpdate(chan+1, start+slice+1, frame+1);
            return img.getProcessor();
        }

        public Image3D denoise(FloatBuffer buffPsis, int length, int nit, float beta, int nBlock, VsnrDllLoader dll, float dx, float dy, float dz)
        {
            Image3D output = new Image3D(width, height, depth, chan, frame, start, bColor);

            int dim = (bColor ? 3 : 1);

            for (int i = 0 ; i < dim ; i++)
                dll.VSNR_3D_FIJI_GPU(buffPsis, length, getBuffer(i), height, width, depth, nit, beta, output.getBuffer(i), nBlock, max[i], dx, dy, dz);

            return output;
        }

        public FloatBuffer getBuffer(int k)
        {
            // -
            return FloatBuffer.wrap(arr[k]);
        }

        public void agregate(ImagePlus result, int dLeft, int dRight, Boolean bLog)
        {
            ImageProcessor ip;
            float tmp;

            if (bColor) {

                int[] pixel = new int[3];
                for (int k = dLeft ; k < depth-dRight ; k++) {
                    ip = getIP(result,k);
                    for (int j = 0 ; j < height ; j++) {
                        for (int i = 0 ; i < width ; i++) {
                            for (int m = 0 ; m < 3 ; m++) {
                                tmp = arr[m][i+width*(j+height*k)];
                                if (bLog) tmp = expf(tmp);
                                pixel[m] = ftoi(tmp);
                            }
                            ip.putPixel(i,j,pixel);
                        }
                    }
                }

            } else {

                for (int k = dLeft ; k < depth-dRight ; k++) {
                    ip = getIP(result,k);
                    for (int j = 0 ; j < height ; j++) {
                        for (int i = 0 ; i < width ; i++) {
                            tmp = arr[0][i+width*(j+height*k)];
                            if (bLog) tmp = expf(tmp);
                            ip.putPixelValue(i,j,tmp-1.0f);
                        }
                    }
                }

            }
        }

        private int ftoi(float val)
        {
            // -
            return Math.round(val-1.0f);
        }

        private float itof(int val)
        {
            // -
            return (float)(val+1);
        }

        private float logf(float val)
        {
            // -
            return (float)(Math.log((double)val));
        }

        private float expf(float val)
        {
            // -
            return (float)(Math.exp((double)val));
        }

    }

    // listener for filter parametrization
    private class ParamListener implements DialogListener {

        @Override
        public boolean dialogItemChanged(GenericDialog g, AWTEvent e)
        {
            CheckboxGroup chkGroup = (CheckboxGroup)(g.getRadioButtonGroups().get(0));
            Vector<TextField> fields = g.getNumericFields();
            if (chkGroup.getSelectedCheckbox().getLabel().equals("Gabor")) {
                fields.get(1).setEnabled(true); // SigmaX
                fields.get(2).setEnabled(true); // SigmaY
                fields.get(3).setEnabled(true); // SigmaZ
                fields.get(4).setEnabled(true); // ThetaX
                fields.get(5).setEnabled(true); // ThetaY
                fields.get(6).setEnabled(true); // ThetaZ
            } else {
                fields.get(1).setEnabled(false); // SigmaX
                fields.get(2).setEnabled(false); // SigmaY
                fields.get(3).setEnabled(false); // SigmaZ
                fields.get(4).setEnabled(false); // ThetaX
                fields.get(5).setEnabled(false); // ThetaY
                fields.get(6).setEnabled(false); // ThetaZ
            }
            return true;
        }

    }

    // dll interface
    private interface VsnrDllLoader extends Library {

        // CUDA denoise function
        public void VSNR_3D_FIJI_GPU(FloatBuffer psis, int length, FloatBuffer u0, int n0, int n1, int n2, int nit, float beta, FloatBuffer u, int nBlock, float max, float dx, float dy, float dz);

        // return dimBlocks max
        public int getMaxBlocks();

        // return dimGrid max
        public int getMaxGrid();

    }

}
