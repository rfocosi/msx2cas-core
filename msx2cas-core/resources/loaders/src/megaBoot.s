; BLOAD MEGARAM FINAL BOOT LOADER (PART N)
; For MSX2Cas Project - Konami4 MegaRAM (Port 0x8E)

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
BANK_ID:
    .DB     00
MAX_BANK:
    .DB     00
RST_MODE:
    .DB     00

CASERR:
    .str    "< MSX2Cas > Fail: CRC ERROR!\0"
MRAMERRMSG:
    .str    "< MSX2Cas > Fail: NO MEGARAM!\0"

SLT_ID:
    .DB     00
CUR_SLT:
    .DB     00
MEGARAM_SLT:
    .DB     00
SAV_RAW:
    .DB     00
SAV_BK0:
    .DB     00
SAV_BK1:
    .DB     00
SAV_BKH:
    .DB     00

START1:
    DI
    LD      HL,(STARTA)
    LD      DE,(ENDA)
    EX      DE,HL
    OR      A
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
    JP      Z,CHK_MRAM

CRCERR:
    LD      DE,#CASERR
    JR      PRTERR

MRAMERR:
    LD      DE,#MRAMERRMSG

PRTERR:
    EI
    PUSH    DE
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
    POP     DE

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

CHK_MRAM:
    CALL    FIND_MEGARAM
    JR      C,MRAMERR
    LD      (MEGARAM_SLT),A

    ; Map MegaRAM to Page 1 (0x4000-0x7FFF) and copy final block
    LD      H,#0x40
    CALL    0x0024        ; ENASLT Page 1 -> MegaRAM
    DI

    OUT     (0x8E),A      ; Mode 0
    LD      A,(BANK_ID)
    LD      (0x4000),A    ; 0x4000-0x5FFF -> bank BANK_ID
    INC     A
    LD      (0x6000),A    ; 0x6000-0x7FFF -> bank BANK_ID + 1

    IN      A,(0x8E)      ; Mode 1 (RAM write)
    LD      HL,(ENDA)
    LD      DE,(STARTA)
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#ROMCODE
    LD      DE,#0x4000
    LDIR

    ; Patch parameters directly into TRAMP before copying to 0xF560 in Page 3
    LD      A,(MEGARAM_SLT)
    LD      (TR_SLT+1),A
    LD      HL,(EXECA)
    LD      (TR_EXEC+1),HL
    LD      A,(RST_MODE)
    LD      (TR_RST+1),A

    ; Compute relative address of TR_RET inside 0xF560 trampoline
    LD      HL,#TR_RET
    LD      DE,#TRAMP
    OR      A
    SBC     HL,DE
    LD      DE,#0x0F560
    ADD     HL,DE
    LD      (TR_RETADDR+1),HL

    ; Copy trampoline from TRAMP..TRAMP_END to 0xF560 and jump there
    LD      HL,#TRAMP_END
    LD      DE,#TRAMP
    OR      A
    SBC     HL,DE
    LD      B,H
    LD      C,L
    LD      HL,#TRAMP
    LD      DE,#0x0F560
    PUSH    DE
    LDIR
    RET

; Page-3 trampoline: maps MegaRAM into Page 1 & Page 2, sets Mode 0,
; initializes Konami4 banks 0..3 -> 0x4000/0x6000/0x8000/0xA000, then resets or boots.
TRAMP:
TR_SLT:
    LD      A,#0x00
    PUSH    AF
    LD      H,#0x40
    CALL    0x0024        ; ENASLT Page 1 -> MegaRAM
    DI
    POP     AF
    LD      H,#0x80
    CALL    0x0024        ; ENASLT Page 2 -> MegaRAM
    DI

    OUT     (0x8E),A      ; Mode 0 (Mapper / ROM mode)
    XOR     A
    LD      (0x4000),A    ; Bank 0 -> 0x4000-0x5FFF
    INC     A
    LD      (0x6000),A    ; Bank 1 -> 0x6000-0x7FFF
    INC     A
    LD      (0x8000),A    ; Bank 2 -> 0x8000-0x9FFF
    INC     A
    LD      (0xA000),A    ; Bank 3 -> 0xA000-0xBFFF

TR_RST:
    LD      A,#0x00
    OR      A
    JR      Z,TR_DIRECT
    JP      0x0000        ; BIOS cold/warm boot detects MegaRAM 'AB' at 0x4000

TR_DIRECT:
TR_RETADDR:
    LD      HL,#0x0000
    PUSH    HL
TR_EXEC:
    LD      HL,#0x0000
    EI
    JP      (HL)

TR_RET:
    DI
    LD      A,(0x0FEDA)   ; H.STKE
    CP      #0xC9         ; RET opcode -> unhooked
    JR      Z,TR_BASIC
    CP      #0xF7         ; RST 30h inter-slot call -> restore Page 1 to BIOS before hook
    JR      NZ,TR_STKE
    LD      A,(0x0FCC1)
    LD      H,#0x40
    CALL    0x0024
TR_STKE:
    EI
    CALL    0x0FEDA
    DI
TR_BASIC:
    LD      A,(0x0FCC1)
    LD      H,#0x40
    CALL    0x0024
    DI
    LD      A,(0x0FCC1)
    LD      H,#0x80
    CALL    0x0024
    EI
    RET
TRAMP_END:

; Scan primary slots 3..0 (and expanded subslots 0..3) for MegaRAM cartridge
FIND_MEGARAM:
    LD      B,#4

FM_PRI:
    LD      A,B
    DEC     A
    LD      (SLT_ID),A
    LD      E,A
    LD      D,#0
    LD      HL,#0x0FCC1   ; EXPTBL
    ADD     HL,DE
    LD      A,(HL)
    AND     #0x80
    JR      NZ,FM_EXP

    LD      A,(SLT_ID)
    PUSH    BC
    CALL    TST_MEGARAM
    POP     BC
    JR      NC,FM_FOUND
    JR      FM_NEXT

FM_EXP:
    LD      C,#0
FM_SUB:
    LD      A,C
    RLCA
    RLCA
    LD      HL,#SLT_ID
    OR      (HL)
    OR      #0x80
    PUSH    BC
    CALL    TST_MEGARAM
    POP     BC
    JR      NC,FM_FOUND
    INC     C
    LD      A,C
    CP      #4
    JR      NZ,FM_SUB

FM_NEXT:
    DJNZ    FM_PRI
    LD      A,(0x0FCC1)
    LD      H,#0x40
    CALL    0x0024
    DI
    SCF
    RET

FM_FOUND:
    LD      A,(CUR_SLT)
    OR      A
    RET

TST_MEGARAM:
    LD      (CUR_SLT),A
    LD      H,#0x40
    CALL    0x0024
    DI

    LD      A,(0x4000)
    LD      (SAV_RAW),A

    OUT     (0x8E),A
    XOR     A
    LD      (0x4000),A
    LD      A,(0x4000)
    LD      (SAV_BK0),A

    IN      A,(0x8E)
    LD      A,#0x55
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0x55
    JP      NZ,TM_FAIL_RAW

    OUT     (0x8E),A
    LD      A,#0x01
    LD      (0x4000),A
    LD      A,(0x4000)
    LD      (SAV_BK1),A

    IN      A,(0x8E)
    LD      A,#0xAA
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0xAA
    JP      NZ,TM_FAIL_RAW

    OUT     (0x8E),A
    XOR     A
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0x55
    JP      NZ,TM_FAIL_RAW

    LD      A,(MAX_BANK)
    CALL    CALC_HIGH_BIT
    CP      #2
    JR      C,TM_RESTORE_01

    LD      B,A
    OUT     (0x8E),A
    LD      A,B
    LD      (0x4000),A
    LD      A,(0x4000)
    LD      (SAV_BKH),A

    IN      A,(0x8E)
    LD      A,#0xAA
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0xAA
    JR      NZ,TM_FAIL_CAP

    OUT     (0x8E),A
    XOR     A
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0x55
    JR      NZ,TM_FAIL_CAP

    OUT     (0x8E),A
    LD      A,B
    LD      (0x4000),A
    IN      A,(0x8E)
    LD      A,(SAV_BKH)
    LD      (0x4000),A

TM_RESTORE_01:
    OUT     (0x8E),A
    XOR     A
    LD      (0x4000),A
    IN      A,(0x8E)
    LD      A,(SAV_BK0)
    LD      (0x4000),A

    OUT     (0x8E),A
    LD      A,#0x01
    LD      (0x4000),A
    IN      A,(0x8E)
    LD      A,(SAV_BK1)
    LD      (0x4000),A

    OUT     (0x8E),A
    LD      A,(0x0FCC1)
    LD      H,#0x40
    CALL    0x0024
    DI
    OR      A
    RET

TM_FAIL_CAP:
    IN      A,(0x8E)
    LD      A,(SAV_BK0)
    LD      (0x4000),A
    JR      TM_FAIL_EXIT

TM_FAIL_RAW:
    LD      A,(SAV_RAW)
    LD      (0x4000),A

TM_FAIL_EXIT:
    OUT     (0x8E),A
    SCF
    RET

CALC_HIGH_BIT:
    OR      A
    RET     Z
    LD      B,#0x80
CHB_LOOP:
    LD      C,A
    AND     B
    JR      NZ,CHB_HIT
    LD      A,B
    RRCA
    LD      B,A
    LD      A,C
    JR      CHB_LOOP
CHB_HIT:
    LD      A,B
    RET
    NOP

ROMCODE:
