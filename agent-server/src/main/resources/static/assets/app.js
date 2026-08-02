(function () {
  const { createApp } = Vue

  async function request(url, options) {
    const response = await fetch(url, options || {})
    const text = await response.text()
    let json
    try {
      json = JSON.parse(text)
    } catch (e) {
      throw new Error('HTTP ' + response.status + ': ' + text.slice(0, 200))
    }
    if (json.code !== 0) {
      throw new Error(json.message || '请求失败')
    }
    return json
  }

  const api = {
    get: (url) => request(url),
    post: (url, body) => request(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {})
    }),
    text: async (url) => {
      const response = await fetch(url)
      if (!response.ok) throw new Error('HTTP ' + response.status)
      return response.text()
    },
    streamChat: async (body, onChunk) => {
      const response = await fetch('/api/v1/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
        body: JSON.stringify(body)
      })
      if (!response.ok) {
        const text = await response.text()
        throw new Error('HTTP ' + response.status + ': ' + text.slice(0, 200))
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split(/\r?\n\r?\n/)
        buffer = parts.pop()
        for (const part of parts) {
          const dataLines = part.split(/\r?\n/)
            .map((line) => line.trim())
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.slice(5).trim())
          const payload = dataLines.length ? dataLines.join('\n') : part.trim()
          if (!payload) continue
          try {
            const obj = JSON.parse(payload)
            if (obj.code === 0 && typeof obj.result === 'string') {
              onChunk(obj.result)
            }
            if (obj.is_end || obj.isEnd) {
              return
            }
          } catch (e) {
            // ignore SSE control lines
          }
        }
      }
    }
  }

  createApp({
    data() {
      return {
        current: 'overview',
        error: '',
        health: { status: '-', registeredAgents: 0, tasks: 0 },
        agents: [],
        tasks: [],
        reports: [],
        ragDocs: [],
        ragResults: [],
        taskLogs: [],
        activeTaskId: '',
        demoTaskInput: 'verify task pipeline',
        reviewRepoPath: '',
        reviewMaxFiles: 100,
        reviewDiffBase: '',
        reviewFocus: 'general',
        reviewBusy: false,
        reviewStatus: '',
        reviewLogs: [],
        reviewResult: '',
        ragQuery: '',
        ragSourceType: 'review',
        ragSourceId: '',
        ragContent: '',
        chatMessages: [],
        chatInput: '',
        chatBusy: false,
        sse: null,
        navItems: [
          { id: 'overview', label: '总览' },
          { id: 'agents', label: 'Agents' },
          { id: 'tasks', label: '任务' },
          { id: 'review', label: '代码审查' },
          { id: 'rag', label: 'RAG' },
          { id: 'chat', label: '对话' }
        ],
        assistantConfig: {
          name: '项目介绍助手',
          description: '介绍 java-agent-platform 的功能和使用方法'
        },
        welcomeConfig: {
          title: 'java-agent-platform 助手',
          description: '问我项目功能、接口用法、代码审查流程或模型配置都可以。'
        },
        presetTasks: [
          { id: 'intro', title: '项目是做什么的', description: '介绍 java-agent-platform 的定位和核心能力' },
          { id: 'review', title: '如何代码审查', description: '说明如何发起一次 Java 代码审查并查看报告' },
          { id: 'llm', title: '如何配置模型', description: '说明如何配置真实 LLM 模型' }
        ]
      }
    },
    computed: {
      healthStatus() {
        return (this.health.status || 'unknown').toLowerCase()
      },
      taskLogsText() {
        return this.taskLogs.map((entry) => `[${entry.level || 'INFO'}] ${entry.message || ''}`).join('\n')
      },
      reviewLogsText() {
        return this.reviewLogs.map((entry) => `[${entry.level || 'INFO'}] ${entry.message || ''}`).join('\n')
      }
    },
    mounted() {
      this.loadAll()
    },
    methods: {
      async loadAll() {
        this.error = ''
        const results = await Promise.allSettled([
          api.get('/api/v1/health'),
          api.get('/api/v1/agents'),
          api.get('/api/v1/tasks'),
          api.get('/api/v1/reviews'),
          api.get('/api/v1/rag/documents')
        ])
        if (results[0].status === 'fulfilled') {
          this.health = results[0].value.data
        }
        if (results[1].status === 'fulfilled') {
          this.agents = results[1].value.data
        }
        if (results[2].status === 'fulfilled') {
          this.tasks = results[2].value.data
        }
        if (results[3].status === 'fulfilled') {
          this.reports = results[3].value.data
        }
        if (results[4].status === 'fulfilled') {
          this.ragDocs = results[4].value.data
        }
        const failed = results.filter((item) => item.status === 'rejected')
        if (failed.length) {
          this.error = failed[0].reason.message
        }
      },
      switchTab(id) {
        this.current = id
        if (id === 'agents') this.loadAgents()
        if (id === 'tasks') this.loadTasks()
        if (id === 'rag') {
          this.loadRagDocs()
          this.ragResults = []
        }
      },
      async loadAgents() {
        try {
          const result = await api.get('/api/v1/agents')
          this.agents = result.data
        } catch (e) {
          this.error = e.message
        }
      },
      async loadTasks() {
        try {
          const result = await api.get('/api/v1/tasks')
          this.tasks = result.data
        } catch (e) {
          this.error = e.message
        }
      },
      async createDemoTask() {
        try {
          const result = await api.post('/api/v1/tasks', {
            agentId: 'demo_agent',
            input: this.demoTaskInput || 'run demo task',
            autoRun: true
          })
          await this.loadTasks()
          await this.selectTask(result.data)
        } catch (e) {
          this.error = e.message
        }
      },
      async selectTask(task) {
        this.activeTaskId = task.taskId
        await this.loadTaskLogs(task.taskId)
        this.startSse(task.taskId)
      },
      async loadTaskLogs(taskId) {
        try {
          const result = await api.get('/api/v1/tasks/' + taskId + '/logs')
          this.taskLogs = result.data
        } catch (e) {
          this.error = e.message
        }
      },
      startSse(taskId) {
        if (this.sse) {
          this.sse.close()
        }
        this.sse = new EventSource('/api/v1/tasks/' + taskId + '/events')
        this.sse.addEventListener('log', (event) => {
          try {
            const data = JSON.parse(event.data)
            this.taskLogs.push({ level: data.level, message: data.message })
          } catch (e) {
            // ignore
          }
        })
        this.sse.addEventListener('status', (event) => {
          try {
            const data = JSON.parse(event.data)
            this.loadTasks()
          } catch (e) {
            // ignore
          }
        })
        this.sse.onerror = () => {
          if (this.sse) {
            this.sse.close()
            this.sse = null
          }
        }
      },
      async runReview() {
        if (!this.reviewRepoPath.trim()) {
          this.error = '请填写仓库路径'
          return
        }
        this.reviewBusy = true
        this.reviewStatus = 'RUNNING'
        this.reviewLogs = []
        this.reviewResult = ''
        try {
          const created = await api.post('/api/v1/reviews', {
            repoPath: this.reviewRepoPath.trim(),
            maxFiles: this.reviewMaxFiles || 100,
            diffBase: this.reviewDiffBase || null,
            focus: this.reviewFocus || 'general'
          })
          const taskId = created.data.taskId
          let lastTask = null
          for (let i = 0; i < 120; i++) {
            await new Promise((resolve) => setTimeout(resolve, 500))
            const task = await api.get('/api/v1/tasks/' + taskId)
            lastTask = task.data
            this.reviewStatus = task.data.status
            const logs = await api.get('/api/v1/tasks/' + taskId + '/logs')
            this.reviewLogs = logs.data
            if (['SUCCEEDED', 'FAILED', 'CANCELED'].includes(task.data.status)) {
              break
            }
          }
          if (this.reviewStatus === 'FAILED' || this.reviewStatus === 'CANCELED') {
            this.reviewResult = '审查任务' + this.reviewStatus + '：' + (lastTask && lastTask.error ? lastTask.error : '未生成报告')
            return
          }
          const reports = await api.get('/api/v1/reviews?taskId=' + taskId)
          const report = reports.data && reports.data[0]
          if (!report) {
            this.reviewResult = '任务已完成，但未找到审查报告。'
            return
          }
          this.reviewResult = await api.text('/api/v1/reviews/' + report.reportId + '/markdown')
          await this.loadAll()
        } catch (e) {
          this.error = e.message
          this.reviewResult = '审查失败：' + e.message
        } finally {
          this.reviewBusy = false
        }
      },
      async searchRag() {
        if (!this.ragQuery.trim()) return
        try {
          const result = await api.get('/api/v1/rag/search?query=' + encodeURIComponent(this.ragQuery.trim()) + '&limit=10')
          this.ragResults = result.data
        } catch (e) {
          this.error = e.message
        }
      },
      async loadRagDocs() {
        try {
          const result = await api.get('/api/v1/rag/documents')
          this.ragDocs = result.data
        } catch (e) {
          this.error = e.message
        }
      },
      async indexRagDoc() {
        if (!this.ragSourceId.trim() || !this.ragContent.trim()) {
          this.error = '请填写来源 ID 和内容'
          return
        }
        try {
          await api.post('/api/v1/rag/documents', {
            sourceType: this.ragSourceType || 'review',
            sourceId: this.ragSourceId.trim(),
            content: this.ragContent
          })
          this.ragSourceId = ''
          this.ragContent = ''
          await this.loadRagDocs()
        } catch (e) {
          this.error = e.message
        }
      },
      async sendChat() {
        const text = this.chatInput.trim()
        if (!text || this.chatBusy) return
        this.chatMessages.push({ role: 'user', content: text })
        const message = { role: 'assistant', content: '', streaming: true }
        this.chatMessages.push(message)
        this.chatInput = ''
        this.chatBusy = true
        this.scrollChat()
        try {
          await api.streamChat({
            query: text,
            appName: 'java-agent-platform',
            userId: 'dashboard-user',
            isStream: true,
            isThinkMode: false,
            history: []
          }, (chunk) => {
            message.content += chunk
            this.scrollChat()
          })
          message.streaming = false
        } catch (e) {
          message.streaming = false
          message.content = '请求失败：' + e.message
        } finally {
          this.chatBusy = false
        }
      },
      scrollChat() {
        this.$nextTick(() => {
          const el = this.$refs.chatMessages
          if (el) el.scrollTop = el.scrollHeight
        })
      },
      formatTime(value) {
        if (!value) return '-'
        const date = new Date(value)
        return isNaN(date.getTime()) ? value : date.toLocaleString()
      }
    }
  }).use(window.SuspendedBallChat.default).mount('#app')
})()
