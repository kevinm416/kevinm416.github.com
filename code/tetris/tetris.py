import wx
import logging as log
import random
import time
import threading

log.basicConfig(format='%(asctime)s %(filename)s:%(lineno)-3d %(message)s', level=log.DEBUG)

class Block:

    def __init__(self, color, offset, size, positions):
        self.color = color
        self.offset = offset
        self.size = size
        self.positions = positions

    def rotate_left(self):
        return Block(self.color, self.offset, self.size, 
                     [(col, -row + self.size) for (row, col) in self.positions])

    def rotate_right(self):
        return Block(self.color, self.offset, self.size,
                     [(-col + self.size, row) for (row, col) in self.positions])

    def fall(self):
        row, col = self.offset
        return Block(self.color, (row + 1, col), self.size, self.positions)

    def move_right(self):
        row, col = self.offset
        return Block(self.color, (row, col + 1), self.size, self.positions)

    def move_left(self):
        row, col = self.offset
        return Block(self.color, (row, col - 1), self.size, self.positions)

    def absolute_positions(self):
        r, c = self.offset
        return ((row + r, col + c) for (row, col) in self.positions)
    
    @classmethod
    def rand(cls):
        block_list = [IBlock, OBlock, JBlock, LBlock, SBlock, ZBlock, TBlock]
        while True:
            i = 0
            random.shuffle(block_list)
            while i < len(block_list):
                yield block_list[i]
                i += 1

# color, initial position, size, positions
IBlock = Block((255,   0,   0), (-1, 3), 3, [(1, 0), (1, 1), (1, 2), (1, 3)])
OBlock = Block((  0,   0, 255), ( 0, 4), 1, [(0, 0), (0, 1), (1, 0), (1, 1)])
JBlock = Block((255, 255,   0), (-1, 3), 2, [(1, 0), (1, 1), (1, 2), (2, 2)])
LBlock = Block((255,   0, 255), (-1, 3), 2, [(1, 0), (1, 1), (1, 2), (2, 0)])
SBlock = Block((  0, 255, 255), ( 0, 3), 2, [(0, 1), (0, 2), (1, 0), (1, 1)])
ZBlock = Block((255, 181,   0), ( 0, 3), 2, [(0, 0), (0, 1), (1, 1), (1, 2)])
TBlock = Block((  0, 255,   0), (-1, 3), 2, [(1, 0), (1, 1), (1, 2), (2, 1)])

class Timer(threading.Thread):

    def __init__(self,  interval, fn):
        threading.Thread.__init__(self)
        self.interval = interval
        self.fn = fn
        self.cond = threading.Condition()
        self.canceled = threading.Event()
        self.paused = threading.Event()
        self.paused.set()
        self.lock = threading.Lock()
        self.evt_time = 0
        self.pause_time = 0
    
    def run(self):
        self.evt_time = time.time() + self.interval
        while True:
            current_time = time.time()
            while current_time < self.evt_time:
                self.cond.acquire()
                self.cond.wait(self.evt_time - current_time)
                self.cond.release()
                self.paused.wait()
                current_time = time.time()
            if not self.canceled.is_set():
                self.fn()
                self.lock.acquire()
                self.evt_time += self.interval
                self.lock.release()
            else : return

    def reschedule(self, interval):
        self.lock.acquire()
        self.evt_time = self.evt_time - self.interval + interval
        self.interval = interval
        self.lock.release()

        self.cond.acquire()
        self.cond.notify()
        self.cond.release()

    def cancel(self):
        self.canceled.set()
    
    def restart(self):
        self.paused.set()
        self.lock.acquire()
        self.evt_time = time.time() + self.interval
        self.lock.release()
    
    def pause(self):
        self.paused.clear()

class Tetris(wx.Frame):
    
    BLOCK_SIZE = 20
    COLUMNS = 10
    ROWS = 20
    FALL_INTERVAL = 1.
    FALL_SPEEDUP = 0.8
    BOARD_SIZE = (BLOCK_SIZE * COLUMNS, BLOCK_SIZE * ROWS)
    SCORE_MULTIPLIER = (40, 100, 300, 1200)

    def __init__(self, parent, title):
        wx.Frame.__init__(self, parent, title=title)
        # only one thread should be moving a block at a time
        self.move_lock = threading.Lock() 
        self.bitmap = wx.EmptyBitmap(*Tetris.BOARD_SIZE)

        self.fall_interval = Tetris.FALL_INTERVAL
        self.block_src = Block.rand()
        self.falling_block = self.block_src.next()
        self.next_block = self.block_src.next()
        self.paused = False
        self.game_over = False

        self.fall_hard_available = True
        self.used_positions = [[None]*Tetris.COLUMNS for i in xrange(Tetris.ROWS)]
        self.open_count = [Tetris.COLUMNS] * Tetris.ROWS
        
        self.next_block_panel = wx.Panel(self, wx.ID_ANY, 
                                size=(4*Tetris.BLOCK_SIZE, 4*Tetris.BLOCK_SIZE))

        self.level = 0
        self.level_text = wx.StaticText(self, wx.ID_ANY, "Level: %s" % self.level)

        self.score = 0
        self.score_text = wx.StaticText(self, wx.ID_ANY, "Score: %s" % self.score)

        self.lines = 0
        self.lines_text = wx.StaticText(self, wx.ID_ANY, "Lines: %s" % self.lines)
        
        self.sizer = wx.BoxSizer(wx.HORIZONTAL)

        self.panel = wx.Panel(self, wx.ID_ANY, size=Tetris.BOARD_SIZE)
        self.panel.SetFocus()
        self.sizer.Add(self.panel)

        self.info_sizer = wx.BoxSizer(wx.VERTICAL)
        self.info_sizer.Add(self.next_block_panel)
        self.info_sizer.Add(self.level_text)
        self.info_sizer.Add(self.score_text)
        self.info_sizer.Add(self.lines_text)
        self.sizer.Add(self.info_sizer)
        
        self.SetSizer(self.sizer)
        self.SetAutoLayout(True)
        self.Layout()

        filemenu = wx.Menu()
        menuAbout = filemenu.Append(wx.ID_ABOUT, "&About"," Information about this program")
        filemenu.AppendSeparator()
        menuExit = filemenu.Append(wx.ID_EXIT,"E&xit"," Terminate the program")

        # A Statusbar in the bottom of the window
        self.CreateStatusBar() 

        # Creating the menubar.
        menuBar = wx.MenuBar()
        menuBar.Append(filemenu,"&File") # Adding the "filemenu" to the MenuBar
        self.SetMenuBar(menuBar)  # Adding the MenuBar to the Frame content.

        self.Bind(wx.EVT_MENU, self.OnAbout, menuAbout)
        self.Bind(wx.EVT_MENU, self.OnExit, menuExit)
        self.Bind(wx.EVT_CLOSE, self.OnClose)
        self.Bind(wx.EVT_PAINT, self.OnPaint)
        self.panel.Bind(wx.EVT_CHAR, self.OnKeyDown)
        self.Show(True)

        self.timer = Timer(self.fall_interval, self.Fall)
        self.timer.start()
        self.Paint()
        self.PaintNextBlock()
    
    def reset(self):
        self.paused = False
        self.game_over = False
        self.fall_interval = Tetris.FALL_INTERVAL
        self.timer.restart()
        self.fall_hard_available = True
        self.used_positions = [[None]*Tetris.COLUMNS for i in xrange(Tetris.ROWS)]
        self.open_count = [Tetris.COLUMNS] * Tetris.ROWS
        self.level = 0
        self.level_text.SetLabel("Level: %s" % self.level)
        self.score = 0
        self.score_text.SetLabel("Score: %s" % self.score)
        self.lines = 0
        self.lines_text.SetLabel("Score: %s" % self.lines)
        self.falling_block = self.next_block
        self.next_block = self.block_src.next()

    def OnAbout(self, e):
        dlg = wx.MessageDialog(self, 
            """\
A Tetris Clone by: Kevin Morgan
up/down - Rotate
left/right - Move
down - Soft Fall
enter - Hard Fall
r - Reset
p - Pause""", 
            "About Tetris", wx.OK)
        self.timer.pause()
        dlg.ShowModal() # Shows it
        self.timer.restart()
        dlg.Destroy() # finally destroy it when finished.

    def OnExit(self, e=None):
        log.debug("OnExit event")
        self.Close(True)  # Close the frame.
    
    def OnClose(self, e):
        log.debug("OnClose event, timer canceled")
        self.timer.cancel()
        self.timer.restart()
        self.timer.join()
        e.Skip()
    
    def PaintNextBlock(self, e=None):
        dc = wx.PaintDC(self.next_block_panel)
        dc.Clear()
        dc.BeginDrawing()
        dc.SetBrush(wx.Brush(wx.Color(*self.next_block.color), wx.SOLID))
        for (i, j) in self.next_block.positions:
            dc.DrawRectangle(j * Tetris.BLOCK_SIZE,
                             i * Tetris.BLOCK_SIZE,
                             Tetris.BLOCK_SIZE,
                             Tetris.BLOCK_SIZE)
        dc.EndDrawing()

    def OnPaint(self, event):
        self.Paint()
        self.PaintNextBlock()

    def Paint(self):
        #log.debug("OnPaint event")
        dc = wx.MemoryDC()
        dc.SelectObject(self.bitmap)
        dc.Clear()
        dc.BeginDrawing()
        dc.SetBrush(wx.Brush(wx.Color(*self.falling_block.color), wx.SOLID))
        for (i, j) in self.falling_block.absolute_positions():
            dc.DrawRectangle(j * Tetris.BLOCK_SIZE,
                             i * Tetris.BLOCK_SIZE,
                             Tetris.BLOCK_SIZE,
                             Tetris.BLOCK_SIZE)

        for i in xrange(len(self.used_positions)):
            for j in xrange(len(self.used_positions[i])):
                if self.used_positions[i][j]:
                    dc.SetBrush(wx.Brush(wx.Color(*self.used_positions[i][j]), wx.SOLID))
                    dc.DrawRectangle(j * Tetris.BLOCK_SIZE,
                                     i * Tetris.BLOCK_SIZE,
                                     Tetris.BLOCK_SIZE,
                                     Tetris.BLOCK_SIZE)
        dc.EndDrawing()

        dest_dc = wx.PaintDC(self.panel)
        dest_dc.Blit(0, 0, Tetris.BOARD_SIZE[0], Tetris.BOARD_SIZE[1],
                     dc, 0, 0)
    
    def OnKeyDown(self, event):
        key_code = event.GetKeyCode()
        new_block = None
        rotation = False
        if key_code == wx.WXK_LEFT:
            log.debug("Moving left")
            new_block = self.falling_block.move_left()
        elif key_code == wx.WXK_RIGHT:
            log.debug("Moving right")
            new_block = self.falling_block.move_right()
        elif key_code == wx.WXK_RETURN:
            log.debug("Hard fall")
            if self.fall_hard_available:
                self.move_lock.acquire()
                new_block = self.falling_block.fall()
                while self.AttemptMove(new_block, False):
                    new_block = new_block.fall()
                self.move_lock.release()
                self.timer.restart()
                self.fall_hard_available = False
        elif key_code == wx.WXK_SPACE:
            log.debug("Fall")
            new_block = self.falling_block.fall()
        elif key_code == wx.WXK_DOWN:
            log.debug("Rotate left")
            new_block = self.falling_block.rotate_left()
            rotation = True
        elif key_code == wx.WXK_UP:
            log.debug("Rotate right")
            new_block = self.falling_block.rotate_right()
            rotation = True
        elif key_code == wx.WXK_ESCAPE:
            self.OnExit()
        elif key_code == ord('r'):
            self.reset()
        elif key_code == ord('p'):
            if self.game_over:
                return # don't unpause the game if it's over
            elif self.paused:
                log.debug("unpause event")
                self.timer.restart()
            else:
                log.debug("pause event")
                self.timer.pause()
            self.paused = not self.paused
        else:
            log.debug("Unrecognized key: %s" % key_code)

        if new_block:
            self.move_lock.acquire()
            self.AttemptMove(new_block, rotation)
            self.move_lock.release()
    
    def AttemptMove(self, new_block, rotation):
        success = True
        for (row, col) in new_block.absolute_positions():
            if row >= Tetris.ROWS or col >= Tetris.COLUMNS or col < 0 \
                    or (row >= 0 and self.used_positions[row][col]):
                success = False
                break
        
        if rotation and (not success):
            lblock = new_block.move_left()
            if self.AttemptMove(lblock, False):
                new_block = lblock
                success = True 
            else:
                rblock = new_block.move_right()
                if self.AttemptMove(rblock, False):
                    new_block = rblock
                    success = True

        if success:
            self.falling_block = new_block
            wx.CallAfter(self.Paint)
        return success

    def Fall(self):
        log.debug("fall start, interval: %s" % self.fall_interval)
        s = time.time()
        self.move_lock.acquire()
        # try to fall, if not possible then we hit the ground
        fall_block = self.falling_block.fall()
        if not self.AttemptMove(fall_block, False):
            rows_cleared = 0
            positions = list(self.falling_block.absolute_positions())
            for (i, j) in positions:
                self.open_count[i] -= 1
                self.used_positions[i][j] = fall_block.color
            
            positions.sort(key=lambda p: p[0], reverse=False) # sort by row descending
            for (i, j) in positions:
                if self.open_count[i] == 0:
                    self.used_positions = [[None]*Tetris.COLUMNS] + self.used_positions[0:i] + self.used_positions[i+1:]
                    self.open_count = [Tetris.COLUMNS] + self.open_count[0:i] + self.open_count[i+1:]
                    rows_cleared += 1

            if rows_cleared > 0:
                self.lines += rows_cleared
                wx.CallAfter(self.lines_text.SetLabel, "Lines: %s" % self.lines)
                self.score += Tetris.SCORE_MULTIPLIER[rows_cleared - 1] * (self.level + 1)
                wx.CallAfter(self.score_text.SetLabel, "Score: %s" % self.score)
                new_level = self.lines/5 + 1
                if not new_level == self.level:
                    self.level = new_level
                    wx.CallAfter(self.level_text.SetLabel, "Level: %s" % self.level)
                    self.fall_interval *= Tetris.FALL_SPEEDUP
                    self.timer.reschedule(self.fall_interval)

            self.falling_block = self.next_block
            self.next_block = self.block_src.next()
            wx.CallAfter(self.PaintNextBlock)
            # check to see if the new block collides with any existing blocks
            if not self.AttemptMove(self.falling_block, False):
                log.debug("game over")
                self.game_over = True
                self.timer.pause()

        # Update the graphics after the move
        wx.CallAfter(self.Paint)
        self.fall_hard_available = True

        self.move_lock.release()
        e = time.time()
        log.debug("fall end, took: %s" % (e- s))

    def print_board(self):
        for i in xrange(len(self.used_positions)):
            print "%2s " % self.open_count[i],
            for j in xrange(len(self.used_positions[i])):
                if self.used_positions[i][j]:
                    print "X",
                else:
                    print "-",
            print
 
if __name__ == '__main__':
    app = wx.App(False)
    gui = Tetris(None, "tetris")
    gui.Show()
    log.debug("starting gui")
    app.MainLoop()
