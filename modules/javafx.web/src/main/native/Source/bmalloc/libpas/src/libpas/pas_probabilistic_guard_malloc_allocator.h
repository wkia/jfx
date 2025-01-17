/*
 * Copyright (c) 2021 Apple Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE INC. OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


#ifndef PAS_PROBABILISTIC_GUARD_MALLOC_ALLOCATOR
#define PAS_PROBABILISTIC_GUARD_MALLOC_ALLOCATOR

#include "pas_utils.h"

PAS_BEGIN_EXTERN_C;

/* Initial Function Definitions */

void pas_probabilistic_guard_malloc_trigger(void);
bool pas_probabilistic_guard_malloc_can_use(void);
bool pas_probabilistic_guard_malloc_should_use(void);

void* pas_probabilistic_guard_malloc_allocate(size_t size, pas_heap* heap, pas_heap_config* heap_config, pas_physical_memory_transaction* transaction);
void pas_probabilistic_guard_malloc_deallocate(void* memory);

/* Helper functions */
size_t pas_probabilistic_guard_malloc_get_free_virtual_memory(void);
size_t pas_probabilistic_guard_malloc_get_free_wasted_memory(void);

PAS_END_EXTERN_C;

#endif
