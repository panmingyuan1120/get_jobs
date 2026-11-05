'use client'

import { useState, useEffect } from 'react'
import { BiBriefcase, BiSave, BiMap, BiMoney, BiTime, BiPlay, BiStop, BiLogOut, BiChat } from 'react-icons/bi'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import PageHeader from '@/app/components/PageHeader'
import { API_BASE } from '@/lib/api'

interface YupaoConfig {
  id?: number
  keywords?: string
  city?: string
  area?: string
  waitTime?: number
  maxPage?: number
  sayHi?: string
  expectedSalaryMin?: number
  expectedSalaryMax?: number
}

export default function YupaoPage() {
  const [config, setConfig] = useState<YupaoConfig>({
    keywords: '',
    city: '',
    area: '',
    waitTime: 10,
    maxPage: 10,
    sayHi: '',
    expectedSalaryMin: undefined,
    expectedSalaryMax: undefined,
  })
  // 关键词展示用（逗号分隔）
  const [keywordsDisplay, setKeywordsDisplay] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [isLoggedIn, setIsLoggedIn] = useState(false)
  const [isRunning, setIsRunning] = useState(false)
  const [checkingLogin, setCheckingLogin] = useState(true)
  const [saveResult, setSaveResult] = useState<{ success: boolean; message: string } | null>(null)
  const [showSaveDialog, setShowSaveDialog] = useState(false)

  const fetchConfig = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/yupao/config`)
      if (res.ok) {
        const data: YupaoConfig = await res.json()
        setConfig(data)
        // 关键词转展示文本
        let kw = data.keywords || ''
        if (kw.trim().startsWith('[')) {
          try {
            const arr = JSON.parse(kw)
            if (Array.isArray(arr)) {
              kw = arr.join(', ')
            } else {
              kw = kw.replace(/^\[|\]$/g, '')
            }
          } catch {
            kw = kw.replace(/^\[|\]$/g, '').replace(/"/g, '')
          }
        }
        setKeywordsDisplay(kw)
      }
    } catch (e) {
      console.error('加载鱼泡配置失败:', e)
    } finally {
      setLoading(false)
    }
  }

  const checkLoginStatus = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/yupao/login-status`)
      if (res.ok) {
        const data = await res.json()
        setIsLoggedIn(!!data.isLoggedIn)
      }
    } catch {
      // 忽略
    } finally {
      setCheckingLogin(false)
    }
  }

  const checkRunningStatus = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/yupao/status`)
      if (res.ok) {
        const data = await res.json()
        setIsRunning(!!data.isRunning)
      }
    } catch {
      // 忽略
    }
  }

  useEffect(() => {
    fetchConfig()
    checkLoginStatus()
    checkRunningStatus()
  }, [])

  // 登录状态轮询（触发登录后每 3 秒检查一次，登录成功即保存 Cookie）
  useEffect(() => {
    if (isLoggedIn) return
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`${API_BASE}/api/yupao/login-status`)
        if (res.ok) {
          const data = await res.json()
          if (data.isLoggedIn) {
            setIsLoggedIn(true)
            // 登录成功后保存 Cookie
            await fetch(`${API_BASE}/api/yupao/save-cookies`, { method: 'POST' })
          }
        }
      } catch {
        // 忽略
      }
    }, 3000)
    return () => clearInterval(interval)
  }, [isLoggedIn])

  // 运行状态轮询
  useEffect(() => {
    const interval = setInterval(checkRunningStatus, 5000)
    return () => clearInterval(interval)
  }, [])

  const handleLogin = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/yupao/login`, { method: 'POST' })
      if (res.ok) {
        const data = await res.json()
        setSaveResult({ success: true, message: data.message || '已打开登录页，请在浏览器中扫码' })
        setShowSaveDialog(true)
      }
    } catch {
      setSaveResult({ success: false, message: '触发登录失败' })
      setShowSaveDialog(true)
    }
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const payload: YupaoConfig = {
        ...config,
        keywords: keywordsDisplay,
      }
      const res = await fetch(`${API_BASE}/api/yupao/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      if (res.ok) {
        setSaveResult({ success: true, message: '保存成功' })
      } else {
        setSaveResult({ success: false, message: '保存失败' })
      }
    } catch {
      setSaveResult({ success: false, message: '保存失败（网络错误）' })
    } finally {
      setSaving(false)
      setShowSaveDialog(true)
    }
  }

  const handleStart = async () => {
    if (!isLoggedIn) {
      setSaveResult({ success: false, message: '请先登录鱼泡' })
      setShowSaveDialog(true)
      return
    }
    try {
      // 先保存配置再启动
      const payload: YupaoConfig = { ...config, keywords: keywordsDisplay }
      await fetch(`${API_BASE}/api/yupao/config`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      const res = await fetch(`${API_BASE}/api/yupao/start`, { method: 'POST' })
      if (res.ok) {
        const data = await res.json()
        setSaveResult({ success: true, message: data.message || '任务已启动' })
        setIsRunning(true)
      } else {
        const data = await res.json()
        setSaveResult({ success: false, message: data.message || '启动失败' })
      }
    } catch {
      setSaveResult({ success: false, message: '启动失败（网络错误）' })
    } finally {
      setShowSaveDialog(true)
    }
  }

  const handleStop = async () => {
    try {
      await fetch(`${API_BASE}/api/yupao/stop`, { method: 'POST' })
      setIsRunning(false)
      setSaveResult({ success: true, message: '停止请求已发送' })
    } catch {
      setSaveResult({ success: false, message: '停止失败' })
    } finally {
      setShowSaveDialog(true)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-5xl">
      <PageHeader
        icon={<BiBriefcase className="text-2xl" />}
        title="鱼泡直聘"
        subtitle="蓝领岗位自动打招呼（建筑/工厂/物流/生活服务）"
        iconClass="text-white"
        accentBgClass="bg-teal-500"
      />

      {/* 登录状态卡片 */}
      <Card className="mb-6">
        <CardContent className="pt-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span
                className={`inline-block w-2.5 h-2.5 rounded-full ${
                  checkingLogin ? 'bg-gray-400' : isLoggedIn ? 'bg-green-500' : 'bg-red-500'
                }`}
              />
              <span className="text-sm font-medium">
                {checkingLogin ? '检查登录状态中…' : isLoggedIn ? '已登录' : '未登录'}
              </span>
              {isRunning && (
                <span className="ml-3 inline-flex items-center gap-1 rounded-md bg-blue-500/10 px-2 py-0.5 text-xs text-blue-500">
                  任务运行中
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {!isLoggedIn && (
                <Button variant="outline" size="sm" onClick={handleLogin}>
                  <BiLogOut className="mr-1" /> 扫码登录
                </Button>
              )}
              {isRunning ? (
                <Button variant="destructive" size="sm" onClick={handleStop}>
                  <BiStop className="mr-1" /> 停止任务
                </Button>
              ) : (
                <Button size="sm" onClick={handleStart} disabled={!isLoggedIn}>
                  <BiPlay className="mr-1" /> 开始打招呼
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 搜索配置 */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BiMap className="h-5 w-5" /> 搜索配置
          </CardTitle>
          <CardDescription>设置搜索关键词、城市和薪资范围</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="space-y-2">
              <Label htmlFor="keywords">搜索关键词</Label>
              <Input
                id="keywords"
                value={keywordsDisplay}
                onChange={(e) => setKeywordsDisplay(e.target.value)}
                placeholder="例如：电工, 焊工, 普工"
              />
              <p className="text-xs text-muted-foreground">逗号分隔，可配多个岗位方向</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="city">城市</Label>
              <Input
                id="city"
                value={config.city || ''}
                onChange={(e) => setConfig({ ...config, city: e.target.value })}
                placeholder="例如：杭州"
              />
              <p className="text-xs text-muted-foreground">目标工作城市</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="area">区县（可选）</Label>
              <Input
                id="area"
                value={config.area || ''}
                onChange={(e) => setConfig({ ...config, area: e.target.value })}
                placeholder="例如：余杭区"
              />
              <p className="text-xs text-muted-foreground">缩小搜索范围</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mt-6">
            <div className="space-y-2">
              <Label htmlFor="salaryMin">期望薪资下限（元/月）</Label>
              <Input
                id="salaryMin"
                type="number"
                value={config.expectedSalaryMin ?? ''}
                onChange={(e) =>
                  setConfig({
                    ...config,
                    expectedSalaryMin: e.target.value ? Number(e.target.value) : undefined,
                  })
                }
                placeholder="例如：6000"
              />
              <p className="text-xs text-muted-foreground">低于此薪资的岗位跳过</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="salaryMax">期望薪资上限（元/月）</Label>
              <Input
                id="salaryMax"
                type="number"
                value={config.expectedSalaryMax ?? ''}
                onChange={(e) =>
                  setConfig({
                    ...config,
                    expectedSalaryMax: e.target.value ? Number(e.target.value) : undefined,
                  })
                }
                placeholder="例如：12000"
              />
              <p className="text-xs text-muted-foreground">高于此薪资的岗位跳过</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxPage">最大翻页数</Label>
              <Input
                id="maxPage"
                type="number"
                value={config.maxPage ?? 10}
                onChange={(e) =>
                  setConfig({ ...config, maxPage: e.target.value ? Number(e.target.value) : 10 })
                }
              />
              <p className="text-xs text-muted-foreground">每个关键词最多翻多少页</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="waitTime">翻页等待（秒）</Label>
              <Input
                id="waitTime"
                type="number"
                value={config.waitTime ?? 10}
                onChange={(e) =>
                  setConfig({ ...config, waitTime: e.target.value ? Number(e.target.value) : 10 })
                }
              />
              <p className="text-xs text-muted-foreground">翻页之间的停顿时间</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 打招呼语 */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BiChat className="h-5 w-5" /> 打招呼语
          </CardTitle>
          <CardDescription>发送给老板的第一句话</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <Textarea
              value={config.sayHi || ''}
              onChange={(e) => setConfig({ ...config, sayHi: e.target.value })}
              placeholder="您好，我对这个岗位很感兴趣，有相关经验，方便沟通一下工作内容和薪资吗？"
              rows={4}
            />
            <p className="text-xs text-muted-foreground">留空则使用默认打招呼语</p>
          </div>
        </CardContent>
      </Card>

      {/* 保存按钮 */}
      <div className="flex justify-end">
        <Button onClick={handleSave} disabled={saving}>
          <BiSave className="mr-2" /> {saving ? '保存中…' : '保存配置'}
        </Button>
      </div>

      {/* 提示弹框 */}
      {showSaveDialog && saveResult && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
          onClick={() => setShowSaveDialog(false)}
        >
          <div
            className="w-80 rounded-lg bg-background p-6 shadow-lg"
            onClick={(e) => e.stopPropagation()}
          >
            <div className={`mb-2 text-base font-semibold ${saveResult.success ? 'text-green-500' : 'text-red-500'}`}>
              {saveResult.success ? '✓ 操作成功' : '✗ 操作失败'}
            </div>
            <div className="text-sm text-muted-foreground">{saveResult.message}</div>
            <Button className="mt-4 w-full" onClick={() => setShowSaveDialog(false)}>
              知道了
            </Button>
          </div>
        </div>
      )}

      {/* 功能说明 */}
      <Card className="mt-6 border-dashed">
        <CardContent className="pt-6">
          <div className="text-sm text-muted-foreground space-y-1">
            <p className="font-medium text-foreground">首次使用须知</p>
            <p>1. 点击「扫码登录」后在弹出的浏览器中用微信扫码登录鱼泡直聘</p>
            <p>2. 登录成功后 Cookie 会自动保存，之后无需重复扫码</p>
            <p>3. 首次运行如遇页面元素定位失败（岗位卡片/按钮无法识别），需在真实页面校准选择器</p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
