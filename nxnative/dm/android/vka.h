// The shim that lets the ported lsfg/ chain (Banana Native's, GPL-3.0-or-later) build against this repo's
// Vulkan surface: Banana's chain reads a `vk` function table and a `V` device struct (vka.h), while this repo
// keeps the same entry points as plain globals (gfx_vk.h's VK_FNS / VK_DEV_FNS) and its device in vkdev_t V.
// The two accessors below hand the chain the shapes its code expects, backed by those; the queue mutex is
// gfx_vk_core.c's g_vk_queue_mu (one queue, submissions serialized on it).
#ifndef DM_VKA_SHIM_H
#define DM_VKA_SHIM_H

#include "gfx_vk.h"
#include "dm.h"
#include <pthread.h>
#include <vulkan/vulkan.h>

typedef struct {
    PFN_vkGetDeviceProcAddr vkGetDeviceProcAddr;
    PFN_vkCreateImage vkCreateImage; PFN_vkGetImageMemoryRequirements vkGetImageMemoryRequirements;
    PFN_vkAllocateMemory vkAllocateMemory; PFN_vkBindImageMemory vkBindImageMemory; PFN_vkCreateImageView vkCreateImageView;
    PFN_vkCreateSampler vkCreateSampler; PFN_vkCreateDescriptorSetLayout vkCreateDescriptorSetLayout;
    PFN_vkCreatePipelineLayout vkCreatePipelineLayout; PFN_vkCreateShaderModule vkCreateShaderModule;
    PFN_vkCreateComputePipelines vkCreateComputePipelines; PFN_vkDestroyShaderModule vkDestroyShaderModule;
    PFN_vkUpdateDescriptorSets vkUpdateDescriptorSets; PFN_vkCmdPipelineBarrier vkCmdPipelineBarrier;
    PFN_vkCmdBindPipeline vkCmdBindPipeline; PFN_vkCmdBindDescriptorSets vkCmdBindDescriptorSets; PFN_vkCmdDispatch vkCmdDispatch;
    PFN_vkCreateDescriptorPool vkCreateDescriptorPool; PFN_vkAllocateDescriptorSets vkAllocateDescriptorSets;
    PFN_vkCreateBuffer vkCreateBuffer; PFN_vkGetBufferMemoryRequirements vkGetBufferMemoryRequirements;
    PFN_vkBindBufferMemory vkBindBufferMemory; PFN_vkMapMemory vkMapMemory; PFN_vkCreateCommandPool vkCreateCommandPool;
    PFN_vkAllocateCommandBuffers vkAllocateCommandBuffers; PFN_vkBeginCommandBuffer vkBeginCommandBuffer;
    PFN_vkEndCommandBuffer vkEndCommandBuffer; PFN_vkQueueSubmit vkQueueSubmit; PFN_vkQueueWaitIdle vkQueueWaitIdle;
    PFN_vkDestroyImageView vkDestroyImageView; PFN_vkDestroyImage vkDestroyImage; PFN_vkFreeMemory vkFreeMemory;
    PFN_vkDestroySampler vkDestroySampler; PFN_vkDestroyBuffer vkDestroyBuffer; PFN_vkGetPhysicalDeviceFeatures2 vkGetPhysicalDeviceFeatures2;
    PFN_vkGetPhysicalDeviceFormatProperties vkGetPhysicalDeviceFormatProperties; PFN_vkDestroyPipeline vkDestroyPipeline;
    PFN_vkDestroyPipelineLayout vkDestroyPipelineLayout; PFN_vkDestroyDescriptorSetLayout vkDestroyDescriptorSetLayout;
    PFN_vkDestroyDescriptorPool vkDestroyDescriptorPool; PFN_vkDestroyCommandPool vkDestroyCommandPool;
    PFN_vkCreateFence vkCreateFence; PFN_vkWaitForFences vkWaitForFences; PFN_vkResetFences vkResetFences;
    PFN_vkResetCommandBuffer vkResetCommandBuffer; PFN_vkCmdBlitImage vkCmdBlitImage;
} vka_vk_t;
vka_vk_t *vka_vk(void);
#define vk (*vka_vk())

typedef struct { VkDevice dev; VkPhysicalDevice pd; uint32_t qf; VkQueue q; pthread_mutex_t *qmu; VkDeviceSize ub_align; int null_desc; } vka_V_t;
vka_V_t *vka_V(void);
#define V (*vka_V())

#define vka_mem_type(bits, want, avoid) vk_mem_type((bits), (want), (avoid))
void vka_img_barrier(VkCommandBuffer cb, VkImage img, VkImageAspectFlags aspect, VkImageLayout from, VkImageLayout to);

// lsfg_chain.c builds its own struct of the four destroy functions off the table (VKA_DECL)
#define VKA_DECL(f) PFN_##f f;

#define VLOG(...) LOG(__VA_ARGS__)
#define VRED(...) LOG("[RED] " __VA_ARGS__)

#endif
