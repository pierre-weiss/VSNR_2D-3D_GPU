

// ------------------------------------------------- //
//                                                   //
//             FIJI PLUGIN : VSNR 2D GPU             //
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


// objectives : denoising 2D Images and Stacks
public class VSNR_2D implements PlugInFilter {

    private String filterType = null;
    private ImagePlus image;
    private int inputMethod;

    private int slice, frame, chan;

    private ArrayList<Float> listFilters = new ArrayList<Float>();
    private float level  = 1;
    private float sigmax = 3;
    private float sigmay = 1;
    private float angle  = 0;

    private float beta   = 10;
    private int   nit    = 20;
    private int   nBlock = 256;

    private boolean bLog = false;

    private VsnrDllLoader dll = null;

    // --------------------------------------------------------------------

    @Override
    public void run(ImageProcessor ip)
    {
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
                new StackWindow(denoiseCuda2D());
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
        return DOES_ALL;
    }
    
    // Open an error window with a msg and exit the plugin
    private void exitWindow(String msg)
    {
        IJ.error("Error", msg);
        throw new RuntimeException(Macro.MACRO_CANCELED);
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
                        filterType = scanLine.next();
                        error = (!(filterType.equals("Dirac")) && !(filterType.equals("Gabor")));
                        break;
                    case 5 :
                        level = Float.parseFloat(scanLine.next());
                        if (filterType.equals("Dirac")) {
                            listFilters.add((float)0);
                            listFilters.add((float)level);
                        }
                        break;
                    case 6 :
                        sigmax = Float.parseFloat(scanLine.next());
                        break;
                    case 7 :
                        sigmay = Float.parseFloat(scanLine.next());
                        break;
                    case 8 :
                        angle = Float.parseFloat(scanLine.next());
                        if (filterType.equals("Gabor")) {
                            listFilters.add((float)1);
                            listFilters.add((float)level);
                            listFilters.add((float)sigmax);
                            listFilters.add((float)sigmay);
                            listFilters.add((float)angle);
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
            exitWindow("Text file not conform !");
            return false;
        }
        if (error) {
            IJ.log("Error : the text file is not conform !");
            exitWindow("Text file not conform !");
            return false;
        }
        return true;
    }

    // to avoid switch(String) for Java 1.6
    private int getStringFlag(String str)
    {
        if (str.equals("Iteration_Number:")) return 1;
        else if (str.equals("Num_Block:"))   return 2;
        else if (str.equals("Log:"))         return 3;
        else if (str.equals("Filter_Type:")) return 4;
        else if (str.equals("Noise_Level:")) return 5;
        else if (str.equals("sigmax:"))      return 6;
        else if (str.equals("sigmay:"))      return 7;
        else if (str.equals("Angle:"))       return 8;
        else if (str.equals("***"))          return 0;
        else return (-1);
    }

    // print into the log windows the text file to use for text file method
    private void printParams()
    {
        int k = 0;
        IJ.log("#VSNR-2D");
        IJ.log("Iteration_Number: " + this.nit);
        if (nBlock == dll.getMaxBlocks())
            IJ.log("Num_Block: auto");
        else
            IJ.log("Num_Block: " + nBlock);
        IJ.log("Log: " + this.bLog);
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
                IJ.log("sigmax: " + listFilters.get(k+2));
                IJ.log("sigmay: " + listFilters.get(k+3));
                IJ.log("Angle: " + listFilters.get(k+4));
                IJ.log("***");
                k += 5;
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
        g.addCheckbox("Multiplicative noise", false);
        g.pack();
        g.showDialog();

        this.nit  = (int)(g.getNextNumber());
        this.bLog = g.getNextBoolean();

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
        g.addNumericField("Sigma X :", sigmax, 2);
        g.addNumericField("Sigma Y :", sigmay, 2);
        g.addNumericField("Angle :", angle, 2);
        g.enableYesNoCancel("OK","+Filter");
        g.addDialogListener(pListener);
        g.pack();
        g.showDialog();

        filterType = g.getNextRadioButton();
        level  = (float)(g.getNextNumber());
        sigmax = (float)(g.getNextNumber());
        sigmay = (float)(g.getNextNumber());
        angle  = (float)(g.getNextNumber());

        if (g.wasCanceled()) return false;

        if (filterType.equals("Dirac")) {
            listFilters.add(0.0f);
            listFilters.add((float)level);
        } else if (filterType.equals("Gabor")) {
            listFilters.add(1.0f);
            listFilters.add((float)level);
            listFilters.add((float)sigmax);
            listFilters.add((float)sigmay);
            listFilters.add((float)angle);
        } else {
            exitWindow("Unknow filter type, this error should NEVER happen ...");
        }

        if (g.wasOKed()) return true;

        askFilterEx();

        return true;
    }

    // Denoise the image
    // using the VSNR_2D_FIJI_GPU function called from the attached dll
    private ImagePlus denoiseCuda2D()
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

        ImagePlus result = image.duplicate();
        result.setTitle("vsnr_" + image.getTitle());

        Image2D input, output;
        int k = 0;

        FloatBuffer buffPsis = getBuffPsi(listFilters);
        int length = listFilters.size();

        for (int z = 0 ; z < slice ; z++) {

            for (int c = 0 ; c < chan ; c++) {

                for (int t = 0 ; t < frame ; t++) {

                    IJ.showStatus("Denoising slice "+(z+1)+"/"+slice+" - chan "+(c+1)+"/"+chan+" - frame "+(t+1)+"/"+frame);

                    input  = new Image2D(result, z, c, t, bLog);
                    output = input.denoise(buffPsis, length, nit, beta, nBlock, dll);

                    output.agregate(result, bLog);

                    IJ.showProgress(++k, slice*chan*frame);

                }

            }

        }

        input  = null;
        output = null;

        return result;
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

    // internal use, init the dll
    private void initDll()
    {
        if (dll == null) {

            URL location = VSNR_2D.class.getProtectionDomain().getCodeSource().getLocation();
            String path  = (new File(location.getFile())).getParentFile().toString();

            if (IJ.isLinux()) path += "/libvsnr2d.so";
            else if (IJ.isWindows()) path += "\\libvsnr2d.dll";
            else exitWindow("Unsuported OS !");

            if (!checkFile(path)) exitWindow("Can not find the dll !\nExpecting :\n" + path);
            dll = (VsnrDllLoader)Native.loadLibrary(path, VsnrDllLoader.class);

        }
    }

    // check if the file specified by path exist and is not a directory
    private Boolean checkFile(String path)
    {
        File f = new File(path);
        return (f.exists() && !f.isDirectory());
    }

    // wraping image manipulation
    private class Image2D {

        private int width;
        private int height;
        private int chan;
        private int frame;
        private int slice;

        private Boolean bColor;

        private float[][] arr;
        private float[]   max;

        public Image2D(ImagePlus img, int slice, int channel, int frame, Boolean bLog)
        {
            this.width  = img.getWidth();
            this.height = img.getHeight();
            this.chan   = channel;
            this.frame  = frame;
            this.slice  = slice;
            this.bColor = (img.getBitDepth() == 24);

            ImageProcessor ip = getIP(img);
            float tmp;

            if (bColor) {

                this.allocate(3, width*height);

                int[] pixel = new int[3];
                for (int j = 0 ; j < height ; j++) {
                    for (int i = 0 ; i < width ; i++) {
                        ip.getPixel(i,j,pixel);
                        for (int m = 0 ; m < 3 ; m++) {
                            tmp = itof(pixel[m]);
                            if (bLog) tmp = logf(tmp);
                            max[m] = Math.max(max[m], tmp);
                            arr[m][i+j*width] = tmp;
                        }
                    }
                }

            } else {

                this.allocate(1, width*height);

                for (int j = 0 ; j < height ; j++) {
                    for (int i = 0 ; i < width ; i++) {
                        tmp = 1.0f + ip.getPixelValue(i,j);
                        if (bLog) tmp = logf(tmp);
                        max[0] = Math.max(max[0], tmp);
                        arr[0][i+j*width] = tmp;
                    }
                }

            }
        }

        public Image2D(int width, int height, int channel, int frame, int slice, Boolean bColor)
        {
            this.width  = width;
            this.height = height;
            this.bColor = bColor;
            this.slice  = slice;
            this.chan   = channel;
            this.frame  = frame;

            int dim = (bColor ? 3 : 1);

            this.allocate(dim, width*height);
        }

        private void allocate(int dim, int size)
        {
            try {
                arr = new float[dim][size];
                max = new float[dim];
                Arrays.fill(max,Float.NEGATIVE_INFINITY);
            } catch (Throwable e) {
                e.printStackTrace();
                String str = "Error :\nProbably running out of memory !\nLaunch IJ in command line to get the stack trace ...";
                IJ.log(str);
                exitWindow(str);
            }
        }

        private ImageProcessor getIP(ImagePlus img)
        {
            img.setPositionWithoutUpdate(chan+1, slice+1, frame+1);
            return img.getProcessor();
        }

        public Image2D denoise(FloatBuffer buffPsis, int length, int nit, float beta, int nBlock, VsnrDllLoader dll)
        {
            Image2D output = new Image2D(width, height, chan, frame, slice, bColor);

            int dim = (bColor ? 3 : 1);

            for (int i = 0 ; i < dim ; i++)
                dll.VSNR_2D_FIJI_GPU(buffPsis, length, getBuffer(i), height, width, nit, beta, output.getBuffer(i), nBlock, max[i]);

            return output;
        }

        public FloatBuffer getBuffer(int k)
        {
            //-
            return FloatBuffer.wrap(this.arr[k]);
        }

        public void agregate(ImagePlus result, Boolean bLog)
        {
            ImageProcessor ip = getIP(result);
            float tmp;

            if (bColor) {

                int[] pixel = new int[3];
                for (int j = 0 ; j < height ; j++) {
                    for (int i = 0 ; i < width ; i++) {
                        for (int m = 0 ; m < 3 ; m++) {
                            tmp = arr[m][i+j*width];
                            if (bLog) tmp = expf(tmp);
                            pixel[m] = ftoi(tmp);
                        }
                        ip.putPixel(i,j,pixel);
                    }
                }

            } else {

                for (int j = 0 ; j < height ; j++) {
                    for (int i = 0 ; i < width ; i++) {
                        tmp = arr[0][i+j*width];
                        if (bLog) tmp = expf(tmp);
                        ip.putPixelValue(i,j,tmp-1.0f);
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
            return (float)Math.log((double)val);
        }

        private float expf(float val)
        {
            // -
            return (float)Math.exp((double)val);
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
                fields.get(1).setEnabled(true); // sigmax
                fields.get(2).setEnabled(true); // sigmay
                fields.get(3).setEnabled(true); // angle
            } else {
                fields.get(1).setEnabled(false); // sigmax
                fields.get(2).setEnabled(false); // sigmay
                fields.get(3).setEnabled(false); // angle
            }
            return true;
        }
        
    }

    // dll interface
    private interface VsnrDllLoader extends Library {

        // CUDA denoise function
        public void VSNR_2D_FIJI_GPU(FloatBuffer psis, int length, FloatBuffer u0, int n0, int n1, int nit, float beta, FloatBuffer u, int nBlock, float max);

        // return dimBlocks max
        public int getMaxBlocks();

        // return dimGrid max
        public int getMaxGrid();

    }

}
