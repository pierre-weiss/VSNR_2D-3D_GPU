# VSNR 2D & 3D with a GPU
## Developers: Jean Eymerie & Pierre Weiss																	
## First public release: 09/06/2017																			
## In case you use the results of this plugin with your article, please don't forget to cite us:
- Fehrenbach, Jérôme, Pierre Weiss, and Corinne Lorenzo. "*Variational algorithms to remove stationary noise: applications to microscopy imaging.*" IEEE Transactions on Image Processing 21.10 (2012): 4420-4430.
- Fehrenbach, Jérôme, and Pierre Weiss. "*Processing stationary noise: model and parameter selection in variational methods.*" SIAM Journal on Imaging Sciences 7.2 (2014): 613-640.
- *Escande, Paul, Pierre Weiss, and Wenxing Zhang. "*A variational model for multiplicative structured noise removal.*" Journal of Mathematical Imaging and Vision 57.1 (2017): 43-55.

This repository contains the sources and executables of the GPU based denoising codes of VSNR in 2D and 3D. 
IMPORTANT NOTE: you will be able to use this plugin only with NVIDIA Graphics cards since it is based on the CUDA programming language.

- For Windows 64 bits users:
0) to use precompiled dlls, make sure that CUDA 8.0 is installed
1) create a repository ~Fiji\plugins\VSNR, where ~Fiji indicates your Fiji repository.
2) copy the contents of precompiled\ in ~Fiji\plugins\VSNR

- For Linux & Mac & Windows 32 bits users:
you will need to recompile the library for your NVIDIA graphics card. Please follow the instructions given in vsnr_2D_gpu/README.txt and  vsnr_3D_gpu/README.txt.

- Note: You will find Python versions of the software here
  
-- https://github.com/CEA-MetroCarac/pyvsnr

-- https://pypi.org/project/pyvsnr/

-- https://zenodo.org/records/10977068

-- https://github.com/patquem/pyvsnr         (using CUPY)
