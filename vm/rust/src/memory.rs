use crate::L3Value;

const MAX_TAG : L3Value = 0xFF;

const HEADER_SIZE : usize = 1;
const MAX_BLOCK_SIZE : usize = 0xFF_FFFF;

pub struct Memory {
    content: Vec<L3Value>,
    free_ix: usize,
}

impl Memory {
    pub fn new(word_size: usize) -> Memory {
        Memory {
            content: vec![0; word_size],
            free_ix: 0
        }
    }

    pub fn set_heap_start(&mut self, heap_start_index: usize) {
        debug_assert!(heap_start_index < self.content.len());
        self.free_ix = heap_start_index
    }

    pub fn allocate(&mut self, tag: L3Value, size: L3Value, _root: usize)
                    -> usize {
        debug_assert!(0 <= tag && tag <= 0xFF);
        debug_assert!(0 <= size);

        let block = self.free_ix + 1;
        self.free_ix = block + (size as usize);
        if self.free_ix >= self.content.len() { panic!("no more memory"); };
        self.set_block_header(block, tag, size as usize);
        block
    }

    pub fn copy(&mut self, block: usize, root: usize) -> usize {
        let size = self.block_size(block);
        let copy = self.allocate(self.block_tag(block), size, root);
        for i in 0..(size as usize) { self[copy + i] = self[block + i] };
        copy
    }

    pub fn free(&mut self, _block: usize) {
        // do nothing
    }

    pub fn block_tag(&self, block: usize) -> L3Value {
        (self[block - HEADER_SIZE] >> 24) & MAX_TAG
    }

    pub fn block_size(&self, block: usize) -> L3Value {
        self[block - HEADER_SIZE] & MAX_BLOCK_SIZE as L3Value
    }

    pub fn set_block_header(&mut self, block: usize, tag: L3Value, size: usize){
        debug_assert!(0 <= tag && tag <= MAX_TAG);
        debug_assert!(size <= MAX_BLOCK_SIZE);

        self[block - HEADER_SIZE] = (tag << 24 ) | (size as L3Value)
    }
}

use std::ops::{ Index, IndexMut };

impl Index<usize> for Memory {
    type Output = L3Value;
    fn index(&self, i: usize) -> &Self::Output {
        &self.content[i]
    }
}

impl IndexMut<usize> for Memory {
    fn index_mut(&mut self, i: usize) -> &mut Self::Output {
        &mut self.content[i]
    }
}
