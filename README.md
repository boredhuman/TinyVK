# Overview

A small vulkan render backend for Minecraft.

# Requirements

VK_KHR_push_descriptor
VK_KHR_dynamic_rendering
A queue family with present support and a queue count of atleast 2.

# Tested Hardwae

6700 XT
RTX 3080
GTX 1650 Ti Max-Q

# Technical Details

https://github.com/LWJGL/lwjgl3/tree/master/modules/lwjgl/shaderc
https://github.com/boredhuman/JavaSpirv
https://github.com/LWJGL/lwjgl3/tree/master/modules/lwjgl/vma

Shaders are dynamically translated. Shaderc converts the GLSL into spirv whilst JavaSpirv mutates the spirv to use
Vulkan semantics.

The cull face is set to clockwise to combat the winding order reversal from the scene rendering being relative to the 
top left. When blitting the present texture to the swapchain image, the image is vertically flipped.

The present work is processed on its own queue and thread to prevent the swapchain functions blocking the main thread.

VMA is used to allocate buffers and textures.

# Performance

Uses a bit less CPU than the OpenGL backend on my machine, resulting in a ~10% increase in FPS due to my machine
being CPU bound. There is still room for improvement.

