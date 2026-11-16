; BLOAD MEGARAM MULTI-BLOCK LOADER (PART 1..N-1)
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

LOADCMD:
    .DB     0x1E
    .str    "bload"
    .DB     34
    .str    "cas:"
    .DB     34
    .str    ",r"
    .DB     13,0

CASLSTR:
    .str    "< MSX2Cas > MegaRAM loading...\0"
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

    LD      A,(BANK_ID)
    OR      A
    JR      NZ,SKIP_UI    ; print banner once on block 0

    EI
    CALL    0x006C        ; set screen 0
    LD      A,#0x0F
    LD      HL,#0x0F3E9
    LD      (HL),A
    LD      A,#4
    INC     HL
    LD      (HL),A
    INC     HL
    LD      (HL),A
    CALL    0x0062        ; set color 15,4,4
    XOR     A
    CALL    0x00C3        ; clear screen
    CALL    0x00CC        ; hide functional keys
    LD      HL,#0x0101
    CALL    0x00C6
    LD      DE,#CASLSTR

BANNER_LOOP:
    LD      A,(DE)
    OR      A
    JR      Z,BANNER_DONE
    INC     DE
    CALL    0x00A2
    INC     H
    CALL    0x00C6
    JR      BANNER_LOOP

BANNER_DONE:
    LD      HL,#0x0103
    CALL    0x00C6
    CALL    0x0156        ; clear keyboard buffer

SKIP_UI:
    DI
    LD      HL,#0x0FBF0
    LD      (0x0F3F8),HL
    LD      (0x0F3FA),HL
    LD      HL,#LOADCMD
    LD      DE,#0x0FBF0
    LD      BC,#29
    LDIR                  ; queue next bload"cas:",r
    LD      HL,#0x0FBF0+29
    LD      (0x0F3F8),HL

    ; Map MegaRAM to Page 1 (0x4000-0x7FFF) and program banks BANK_ID / BANK_ID+1
    LD      A,(MEGARAM_SLT)
    LD      H,#0x40
    CALL    0x0024        ; ENASLT Page 1 -> MegaRAM
    DI

    OUT     (0x8E),A      ; Mode 0 (Mapper / bank select)
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

    OUT     (0x8E),A      ; Leave MegaRAM in Mode 0 (write-protected)
    LD      A,(0x0FCC1)   ; EXPTBL[0]: Main BIOS/BASIC slot
    LD      H,#0x40
    CALL    0x0024        ; Restore Page 1 to BASIC
    EI
    RET

; Scan primary slots 3..0 (and expanded subslots 0..3) for MegaRAM cartridge
; Returns CF=0, A=slot if found (and has >= MAX_BANK+1 banks); CF=1 otherwise.
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
    ; Restore Page 1 to Main ROM before reporting failure
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
    CALL    0x0024        ; ENASLT Page 1 -> candidate slot
    DI

    LD      A,(0x4000)
    LD      (SAV_RAW),A   ; Save raw byte in case candidate is normal/mapper RAM

    OUT     (0x8E),A      ; Mode 0 (Mapper mode)
    XOR     A
    LD      (0x4000),A    ; Select Bank 0 (if MegaRAM)
    LD      A,(0x4000)
    LD      (SAV_BK0),A   ; Save Bank 0 original byte

    IN      A,(0x8E)      ; Mode 1 (RAM write)
    LD      A,#0x55
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0x55
    JP      NZ,TM_FAIL_RAW

    OUT     (0x8E),A      ; Mode 0
    LD      A,#0x01
    LD      (0x4000),A    ; Select Bank 1 (if MegaRAM)
    LD      A,(0x4000)
    LD      (SAV_BK1),A   ; Save Bank 1 original byte

    IN      A,(0x8E)      ; Mode 1
    LD      A,#0xAA
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0xAA
    JP      NZ,TM_FAIL_RAW

    OUT     (0x8E),A      ; Mode 0
    XOR     A
    LD      (0x4000),A    ; Re-select Bank 0
    LD      A,(0x4000)
    CP      #0x55         ; Bank 0 still holds 0x55? (Normal RAM holds 0xAA -> fail)
    JP      NZ,TM_FAIL_RAW

    ; MegaRAM confirmed! Verify capacity covers highest bit of MAX_BANK.
    LD      A,(MAX_BANK)
    CALL    CALC_HIGH_BIT ; returns A = highest set bit (0, 1, 2, 4, ..., 128)
    CP      #2
    JR      C,TM_RESTORE_01 ; Bank 0 and Bank 1 already proven distinct

    LD      B,A
    OUT     (0x8E),A      ; Mode 0
    LD      A,B
    LD      (0x4000),A    ; Select Bank H
    LD      A,(0x4000)
    LD      (SAV_BKH),A

    IN      A,(0x8E)      ; Mode 1
    LD      A,#0xAA
    LD      (0x4000),A
    LD      A,(0x4000)
    CP      #0xAA
    JR      NZ,TM_FAIL_CAP

    OUT     (0x8E),A      ; Mode 0
    XOR     A
    LD      (0x4000),A    ; Re-select Bank 0
    LD      A,(0x4000)
    CP      #0x55         ; Wrapped onto Bank 0?
    JR      NZ,TM_FAIL_CAP

    ; Restore Bank H
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

    OUT     (0x8E),A      ; Mode 0 (write-protected)
    LD      A,(0x0FCC1)
    LD      H,#0x40
    CALL    0x0024        ; Restore Page 1 to BIOS/BASIC
    DI
    OR      A
    RET

TM_FAIL_CAP:
    ; Bank H wrapped onto Bank 0, restore Bank 0 original byte
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

; Given A = N, returns A = highest power-of-2 bit set in N (or 0 if N == 0)
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
