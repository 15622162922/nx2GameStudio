// The vka.h shim's implementation: the shapes Banana's lsfg chain reads, backed by this repo's globals.
#include "vka.h"
#include <string.h>

// this file builds the shapes themselves: the two macros above would read themselves here
#undef vk
#undef V

extern pthread_mutex_t g_vk_queue_mu;   // gfx_vk_core.c: the one queue's submit lock

static vka_vk_t t_vk; static vka_V_t t_V; static int t_up;

static void bring_up(void) {
    if (t_up) return;
    t_up = 1;
#define A(f) t_vk.f = f;
    A(vkGetDeviceProcAddr) A(vkCreateImage) A(vkGetImageMemoryRequirements) A(vkAllocateMemory) A(vkBindImageMemory) A(vkCreateImageView)
    A(vkCreateSampler) A(vkCreateDescriptorSetLayout) A(vkCreatePipelineLayout) A(vkCreateShaderModule) A(vkCreateComputePipelines)
    A(vkDestroyShaderModule) A(vkUpdateDescriptorSets) A(vkCmdPipelineBarrier) A(vkCmdBindPipeline) A(vkCmdBindDescriptorSets) A(vkCmdDispatch)
    A(vkCreateDescriptorPool) A(vkAllocateDescriptorSets) A(vkCreateBuffer) A(vkGetBufferMemoryRequirements) A(vkBindBufferMemory) A(vkMapMemory) A(vkCreateCommandPool)
    A(vkAllocateCommandBuffers) A(vkBeginCommandBuffer) A(vkEndCommandBuffer) A(vkQueueSubmit) A(vkQueueWaitIdle)
    A(vkDestroyImageView) A(vkDestroyImage) A(vkFreeMemory) A(vkDestroySampler) A(vkDestroyBuffer)
    A(vkGetPhysicalDeviceFeatures2) A(vkGetPhysicalDeviceFormatProperties)
    A(vkCreateFence) A(vkWaitForFences) A(vkResetFences) A(vkResetCommandBuffer) A(vkCmdBlitImage)
#undef A
    // the four destroy functions the chain frees itself with: this replay never destroys pipelines or pools,
    // so they are not globals here — they load off the device (and without them the chain reports so)
#define A2(f) t_vk.f = (PFN_##f)t_vk.vkGetDeviceProcAddr(V.dev, #f);
    A2(vkDestroyPipeline) A2(vkDestroyPipelineLayout) A2(vkDestroyDescriptorSetLayout)
    A2(vkDestroyDescriptorPool) A2(vkDestroyCommandPool)
#undef A2
    t_V.dev = V.dev; t_V.pd = V.pd; t_V.qf = V.qf; t_V.q = V.q; t_V.qmu = &g_vk_queue_mu;
    t_V.ub_align = V.props.limits.minUniformBufferOffsetAlignment; t_V.null_desc = V.has_robust2;
}

vka_vk_t *vka_vk(void) { bring_up(); return &t_vk; }
vka_V_t *vka_V(void) { bring_up(); return &t_V; }

// The chain puts every image in GENERAL layout once when it is made (lsfg_chain.c): one barrier per image
void vka_img_barrier(VkCommandBuffer cb, VkImage img, VkImageAspectFlags aspect, VkImageLayout from, VkImageLayout to) {
    VkImageMemoryBarrier b = {VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER, NULL, 0, 0, from, to, VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED, img,
                              {aspect, 0, VK_REMAINING_MIP_LEVELS, 0, VK_REMAINING_ARRAY_LAYERS}};
    VkPipelineStageFlags s = from == VK_IMAGE_LAYOUT_UNDEFINED ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    vkCmdPipelineBarrier(cb, s, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, NULL, 0, NULL, 1, &b);
}
