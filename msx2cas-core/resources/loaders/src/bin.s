; SINGLE BIN FILE LOADER (FOR CASLINK3 PROJECT)
; COPYRIGHT (C) 1999-2016 ALEXEY PODREZOV
; Modified by Roberto Focosi for MSX2Cas Project

START:
    JP      START1

STARTA:
    .DW     00
ENDA:
    .DW     00
EXECA:
    .DW     00
CRC:
    .DB     00

CASERR:
    .str    "< MSX2Cas > Fail: CRC ERROR!\0"

START1:
    DI
    LD      HL,(STARTA)
    LD      DE,(ENDA)
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    PUSH    HL
    POP     BC
    LD      HL,#ROMCODE
    XOR     A
    PUSH    AF

START2:
    POP     AF
    ADD     A,(HL)
    INC     HL
    DEC     BC
    PUSH    AF
    LD      A,C
    OR      A
    JR      NZ,START2
    LD      A,B
    OR      A
    JR      NZ,START2
    POP     AF
    LD      B,A
    LD      HL,#CRC
    LD      A,(HL)
    CP      B
    JP      Z,START5

CRCERR:
    EI
    CALL    0x006C        ; set screen 0
    LD      A,#0x0F
    LD      HL,#0x0F3E9
    LD      (HL),A
    LD      A,#8
    INC     HL
    LD      (HL),A
    INC     HL
    LD      (HL),A
    CALL    0x0062        ; set color 15,8,8
    XOR     A
    CALL    0x00C3        ; clear screen
    CALL    0x00CF        ; unhide functional keys
    LD      HL,#0x0101
    CALL    0x00C6        ; set cursor position to 1:1
    LD      DE,#CASERR

START3:
    LD      A,(DE)
    OR      A
    JR      Z,START4
    INC     DE
    CALL    0x00A2        ; display character
    INC     H
    CALL    0x00C6        ; set next position
    JR      START3

START4:
    LD      HL,#0x0103
    CALL    0x00C6        ; set cursor position to 1:3
    CALL    0x00C0        ; beep
    CALL    0x0156        ; clears keyboard buffer
    EI
    RET

START5:
    DI
    LD      HL,(EXECA)
    PUSH    HL
    LD      HL,#ROMCODE
    LD      DE,(STARTA)
    LD      A,H
    CP      D
    JR      C,START7    ; target area lower than destination address?
    JR      NZ,START6
    LD      A,L
    CP      E
    JR      C,START7    ; target area lower than destination address?

START6:
    LD      HL,#STARTI    ; incremental transfer routine
    LD      DE,#STARTD+1
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#STARTI
    LD      DE,#0x0F560
    PUSH    DE
    LDIR
    RET

START7:
    LD      HL,#STARTD    ; decremental transfer routine
    LD      DE,#ROMCODE+1
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#STARTD
    LD      DE,#0x0F560
    PUSH    DE
    LDIR
    RET

STARTI:
    LD      HL,(STARTA)
    LD      DE,(ENDA)
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LD      DE,(STARTA)
    INC     BC
    LDIR
    EI
    RET
    NOP

STARTD:
    LD      HL,(STARTA)
    LD      DE,(ENDA)
    EX      DE,HL
    SCF
    CCF
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,(STARTA)
    SCF
    CCF
    ADC     HL,BC        ; end of data at target address
    EX      DE,HL
    LD      HL,#ROMCODE
    SCF
    CCF
    ADC     HL,BC        ; end of data at source address
    INC     BC
    LDDR
    EI
    RET
    NOP

ROMCODE:
