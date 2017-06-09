%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Developers: Jean Eymerie & Pierre Weiss																		
% First public release: 09/06/2017																			
% In case you use the results of this plugin with your article, please don't forget to cite us:
%
% - Fehrenbach, Jérôme, Pierre Weiss, and Corinne Lorenzo. "Variational algorithms to remove stationary noise: applications to microscopy imaging." IEEE Transactions on Image Processing 21.10 (2012): 4420-4430.
%
% - Fehrenbach, Jérôme, and Pierre Weiss. "Processing stationary noise: model and parameter selection in variational methods." SIAM Journal on Imaging Sciences 7.2 (2014): 613-640.
%
%  - Escande, Paul, Pierre Weiss, and Wenxing Zhang. "A variational model for multiplicative structured noise removal." Journal of Mathematical Imaging and Vision 57.1 (2017): 43-55.
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


This repository contains the sources for VSNR GPU 2D.

*** STEP 1/ Compilation of .jar (with dependencies) ***

NOTE: THE COMPILED JAR THAT YOU CAN FIND AT https://www.math.univ-toulouse.fr/~weiss/PageCodes.html SHOULD WORK BY ITSELF (YOU SHOULDN'T HAVE TO COMPILE THE JAR BY YOURSELF)

    Compilation is performed with maven (https://maven.apache.org/)

    LINUX: after a clean maven install use the comand "mvn clean package" into the folder that contains the pom.xml file
    WINDOWS: the best way to do it is to open the project with eclipse (with the maven plugin) (the project should be recognized as a maven project automatically), then "run as" -> "maven build" into the goal field: "clean package"

    the .jar with dependencies will be compiled in the /target folder (it will be the heavier of the two .jar)

    NOTE: In case you have trouble, check the pom.xml file, and refer to the official maven documentation i.e. http://maven.apache.org/guides/

*** STEP 2/ Compilation of .cu (into .so (linux) or .dll (windows)) ***

NOTE: THE .dll THAT YOU CAN FIND ON PIERRE WEISS WEBSITE SHOULD WORK ON WINDOWS (YOU SHOULDN'T HAVE TO COMPILE BY YOURSELF ON WINDOWS)

    LINUX: 
    cd src
    nvcc -o libvsnr2d.so -lcufft -lcublas --compiler-options "-fPIC" --shared vsnr2d.cu

    NOTE: you may be asked to not use a version of gcc later than 4.4. Then, you'll need to install the correct compiler (using e.g. synaptic) and specify the absolute path with the -ccbin option, by default nvcc use gcc to compile, but you can force the usage of an other compiler (e.g. cl).

    NOTE: if you need to use specific libraries use the -L option to specify the location, for instance:
    /usr/local/cuda/bin/nvcc -v -o libvsnr2d.so -lcufft -L /usr/local/cuda-6.5/lib64/ -lcublas  --compiler-options "-fPIC" --shared vsnr3d.cu

    WINDOWS:
    cd src
    nvcc -o libvsnr2d.dll -L cufftw.lib cufft.lib cublas.lib --shared vsnr2d.cu

    NOTE: certain dependencies should be satisfied (e.g. uuid.lib or kernel32.lib) then you have to specify with -L option the path to the folder containing this dependencies (in case this is not already linked).

    NOTE: for more informations refer to the official NVIDIA nvcc documentation i.e. http://docs.nvidia.com/cuda/cuda-compiler-driver-nvcc

*** STEP 3/ Plugin  install *** 

  - create a folder (a default name could be "vsnr", but it doesn't matter) into the /plugins folder that you can find at the root of your ImageJ distribution
    ( .../ImageJ/plugins/vsnr )
    ( note: the real goal is to get your .jar in the same folder that your .so/.dll, that's why we recommand to create a folder )
  - copy/move target/VSNR_GPU_2D.jar and the src/libvsnr2d.so (or libvsnr2d.dll) into this folder
  - launch ImageJ (the plugin will be find in "plugin" -> "process" -> "VSNR GPU 2D")
  - enjoy :)

*** Use of the plugin ***

You can use the plugin either with the graphical interface, either with a text file. An example of text file is given in Example_Parameters.txt.

