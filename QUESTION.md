To replace the mark-and-sweep collector with a copying collector, I would
partition the heap part of the VM address space into two semispaces. At any
time, allocations would happen linearly in the active semispace using a bump
pointer. When allocation fails, the collector would make the inactive semispace
the destination space, reset its bump pointer, and evacuate all reachable
objects from the old space into it.

The root set would be the same root set used by the mark-and-sweep collector:
the current top frame, plus the other top frame when the current frame's caller
slot points to it. For each root word that is known to contain a heap pointer,
the collector would copy the referenced block to to-space and overwrite the root
with the new virtual address. Each copied block would leave a forwarding pointer
in its old header or first payload word, using a reserved forwarding tag or
another unambiguous encoding. If another reference to the same old block is
encountered later, the collector would reuse the forwarding pointer instead of
copying the block twice. After the roots are updated, the collector would scan
the copied objects in to-space, evacuating and rewriting their pointer fields
until the scan pointer catches up with the allocation pointer. Finally, it would
swap the roles of from-space and to-space; the old semispace is then entirely
free.

Rewriting pointers is not inherently a problem for L3VM, because VM pointers are
just virtual addresses. After copying a block, the collector can compute the new
virtual address with `addr_p_to_v` and store it wherever the old virtual address
appeared. The hidden block header convention is also manageable: program-visible
block pointers point to the payload word, so the copied block must preserve its
header immediately before the payload and forwarding pointers must rewrite to
the new payload address.

The real problem is knowing which words should be rewritten. The current VM is
not precise enough for a naive copying collector. Register frames and heap
blocks contain raw machine-level L3 values, and not every even word is a heap
pointer. Most source-level integers are tagged, but the VM can also have
untagged integers in activation frames, return addresses in frame slot 0, code
pointers inside function blocks, characters, booleans, unit values, and ordinary
data words. The mark-and-sweep collector can be conservative here: if a word
looks like a pointer but is really an integer, marking the corresponding object
keeps too much live but does not corrupt the program. A copying collector cannot
do that safely. If it rewrites a false pointer, it changes an integer or code
address into a different heap address, which is a semantic bug. If it fails to
rewrite a real pointer, the program later follows a dangling pointer into the
old semispace.

The clean solution is to make the collector precise. The compiler could emit
stack maps for every allocation point, describing which live registers in the
current frame contain heap pointers. Heap objects could carry or reference
layout descriptors describing their pointer fields. For example, strings would
contain no heap pointers; closures would have a code slot that must not be
rewritten and environment slots that may be pointers; register-frame blocks
would need metadata for their live pointer slots. With that information, the
copying collector can update exactly the words that are real references.

Other solutions are possible but less attractive. The VM could introduce an
indirection layer, so values refer to stable handles and only handles are
updated when objects move; this avoids rewriting arbitrary program words but
adds overhead to every heap access. It could also pin ambiguous objects or use a
mostly-copying conservative algorithm, moving only objects whose references are
known precisely. That preserves correctness but loses some of the simplicity and
compaction benefits of a normal copying collector. Therefore, a copying
collector is feasible for L3VM, but only if the VM and compiler provide precise
pointer-location information, or if the collector accepts extra indirection or
pinning to avoid corrupting non-pointer values.
