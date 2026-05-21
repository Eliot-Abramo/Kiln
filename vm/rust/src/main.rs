mod memory;
mod engine;

use std::{env, process};
use std::fs::File;
use std::io::{BufReader, BufRead};

use crate::engine::Engine;
use crate::memory::Memory;

pub type L3Value = i32;
pub const LOG2_VALUE_BYTES : usize = 2;
pub const LOG2_VALUE_BITS  : usize = 5;
pub const VALUE_BITS       : usize = 1 << LOG2_VALUE_BITS;

const DEFAULT_MEMORY_SIZE  : usize = 1_000_000;

const TAG_REGISTER_FRAME : L3Value = 0xFF;

fn load(file_name: &str, engine: &mut Engine) -> Result<(), String> {
    let file = File::open(file_name)
        .map_err(|_| format!("can't open file {}", file_name))?;

    for (index, line) in BufReader::new(file).lines().enumerate() {
        let line = line
            .map_err(|_| format!("I/O problem at line {}", index))?;
        let instr = u32::from_str_radix(&line[0..8], 16)
            .map_err(|_| format!("can't parse line {}:\n{}", index, line))?;
        engine.emit_instruction(instr as L3Value);
    }
    Ok(())
}

enum Command {
    ShowHelp,
    Run { memory_size: usize, file_name: String },
}

fn parse_args() -> Result<Command, String> {
    let mut memory_size = DEFAULT_MEMORY_SIZE;
    let mut maybe_file_name = None;

    let mut args_it = env::args().skip(1);
    while let Some(arg) = args_it.next() {
        if arg == "-h" {
            return Ok(Command::ShowHelp)
        } else if arg == "-m" {
            if let Some(s) = args_it.next() {
                memory_size = s.parse::<usize>()
                    .map_err(|_| format!("invalid memory size: {}", s))?
            } else {
                Err("missing argument to -m")?
            }
        } else if maybe_file_name.is_none() {
            maybe_file_name = Some(arg.to_string())
        } else {
            Err("more than one file name given")?
        }
    }
    maybe_file_name
        .map(|file_name| Command::Run { memory_size, file_name })
        .ok_or("no file name given".to_string())
}

fn actual_main() -> Result<i32, String> {
    match parse_args()? {
        Command::ShowHelp => {
            println!("Usage: {} [<options>] <asm_file>",
                     env::args().next().unwrap_or("l3vm".to_string()));
            println!("options:");
            println!("  -h         display this help message and exit");
            println!("  -m <size>  set memory size in bytes");
            Ok(0)
        },
        Command::Run { memory_size, file_name } => {
            let mut engine = Engine::new(Memory::new(memory_size >> 2));
            load(&file_name, &mut engine)?;
            let halt_code = engine.run();
            Ok(halt_code)
        },
    }
}

fn main() {
    match actual_main() {
        Ok(exit_code) =>
            process::exit(exit_code),
        Err(e) => {
            eprintln!("Error: {}", e);
            process::exit(1)
        }
    }
}
