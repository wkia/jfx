/*
 * Copyright (C) 2005, 2006, 2008 Apple Inc. All rights reserved.
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

#ifndef SplitElementCommand_h
#define SplitElementCommand_h

#include "EditCommand.h"

namespace WebCore {

class SplitElementCommand : public SimpleEditCommand {
public:
    static Ref<SplitElementCommand> create(PassRefPtr<Element> element, PassRefPtr<Node> splitPointChild)
    {
        return adoptRef(*new SplitElementCommand(element, splitPointChild));
    }

private:
    SplitElementCommand(PassRefPtr<Element>, PassRefPtr<Node> splitPointChild);

    virtual void doApply() override;
    virtual void doUnapply() override;
    virtual void doReapply() override;
    void executeApply();

#ifndef NDEBUG
    virtual void getNodesInCommand(HashSet<Node*>&) override;
#endif

    RefPtr<Element> m_element1;
    RefPtr<Element> m_element2;
    RefPtr<Node> m_atChild;
};

} // namespace WebCore

#endif // SplitElementCommand_h