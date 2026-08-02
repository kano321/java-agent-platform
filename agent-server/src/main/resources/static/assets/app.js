(function () {
  const { createApp } = Vue
  const CHAT_STORAGE_KEY = 'java-agent-platform.chat.history.v1'

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
    del: (url) => request(url, { method: 'DELETE' }),
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
        reviewAgentId: 'code_review_agent',
        reviewBusy: false,
        reviewStatus: '',
        reviewLogs: [],
        reviewResult: '',
        agentKinds: ['GENERAL', 'CODE_REVIEW', 'RESEARCH', 'DATA_ANALYSIS', 'DEMO', 'REMOTE'],
        agentForm: {
          agentId: '',
          name: '',
          kind: 'REMOTE',
          version: '1.0.0',
          tags: '',
          executionEndpoint: ''
        },
        ragQuery: '',
        ragSourceType: 'review',
        ragSourceId: '',
        ragContent: '',
        chatMessages: [],
        chatInput: '',
        chatBusy: false,
        chatHistory: [],
        chatHistoryQuery: '',
        currentChatId: null,
        sse: null,
        navItems: [
          { id: 'overview', label: '总览' },
          { id: 'agents', label: 'Agents' },
          { id: 'tasks', label: '任务' },
          { id: 'review', label: '代码审查' },
          { id: 'rag', label: 'RAG' },
          { id: 'chat', label: '对话' }
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
      },
      filteredChatHistory() {
        const query = this.chatHistoryQuery.trim().toLowerCase()
        if (!query) return this.chatHistory
        return this.chatHistory.filter((item) => {
          const title = (item.title || '').toLowerCase()
          const content = (item.messages || [])
            .map((message) => message.content || '')
            .join(' ')
            .toLowerCase()
          return title.includes(query) || content.includes(query)
        })
      }
    },
    mounted() {
      this.loadAll()
      this.initChatHistory()
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
        if (id === 'review') this.loadAgents()
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
          if (!this.agents.some((agent) => agent.agentId === this.reviewAgentId)) {
            this.reviewAgentId = this.agents.length ? this.agents[0].agentId : 'code_review_agent'
          }
        } catch (e) {
          this.error = e.message
        }
      },
      async registerAgent() {
        const agentId = this.agentForm.agentId.trim()
        const name = this.agentForm.name.trim()
        const endpoint = this.agentForm.executionEndpoint.trim()
        if (!agentId || !name) {
          this.error = '请填写 Agent ID 和名称'
          return
        }
        if (!endpoint) {
          this.error = '请填写执行端点，远程 Agent 需要它来接收任务'
          return
        }
        try {
          await api.post('/api/v1/agents/register', {
            agentId,
            name,
            description: 'Custom agent registered from dashboard',
            kind: this.agentForm.kind,
            version: this.agentForm.version.trim() || '1.0.0',
            tags: this.agentForm.tags
              .split(',')
              .map((tag) => tag.trim())
              .filter(Boolean),
            executionEndpoint: endpoint
          })
          this.agentForm.agentId = ''
          this.agentForm.name = ''
          this.agentForm.tags = ''
          this.agentForm.executionEndpoint = ''
          await this.loadAgents()
        } catch (e) {
          this.error = e.message
        }
      },
      async heartbeatAgent(agentId) {
        try {
          await api.post('/api/v1/agents/' + encodeURIComponent(agentId) + '/heartbeat')
          await this.loadAgents()
        } catch (e) {
          this.error = e.message
        }
      },
      async unregisterAgent(agentId) {
        if (!window.confirm('确认注销 Agent ' + agentId + ' 吗？')) return
        try {
          await api.del('/api/v1/agents/' + encodeURIComponent(agentId))
          await this.loadAgents()
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
      async deleteTask(task) {
        if (!window.confirm('确认删除任务 ' + task.taskId + ' 吗？')) return
        try {
          await api.del('/api/v1/tasks/' + encodeURIComponent(task.taskId))
          if (this.activeTaskId === task.taskId) {
            this.activeTaskId = ''
            this.taskLogs = []
            if (this.sse) {
              this.sse.close()
              this.sse = null
            }
          }
          await this.loadTasks()
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
            focus: this.reviewFocus || 'general',
            agentId: this.reviewAgentId || 'code_review_agent'
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
          if (this.reviewAgentId !== 'code_review_agent') {
            this.reviewResult = lastTask && (lastTask.output || lastTask.error)
              ? lastTask.output || lastTask.error
              : '任务已完成，但没有输出。'
            await this.loadAll()
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
      exportReviewMarkdown() {
        if (!this.reviewResult) return
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
        const blob = new Blob([this.reviewResult], { type: 'text/markdown;charset=utf-8' })
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = 'java-agent-platform-review-' + timestamp + '.md'
        document.body.appendChild(link)
        link.click()
        link.remove()
        URL.revokeObjectURL(url)
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
      initChatHistory() {
        let saved = []
        try {
          saved = JSON.parse(localStorage.getItem(CHAT_STORAGE_KEY) || '[]')
        } catch (e) {
          saved = []
        }
        this.chatHistory = Array.isArray(saved)
          ? saved.filter((item) => item && item.id && Array.isArray(item.messages))
          : []
        if (this.chatHistory.length === 0) {
          this.newChat()
          return
        }
        this.currentChatId = this.chatHistory[0].id
        this.chatMessages = this.chatHistory[0].messages
      },
      persistChatHistory() {
        try {
          localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(this.chatHistory))
        } catch (e) {
          // localStorage can be unavailable in some embedded browsers
        }
      },
      currentChat() {
        return this.chatHistory.find((item) => item.id === this.currentChatId) || null
      },
      touchCurrentChat() {
        const chat = this.currentChat()
        if (!chat) return
        chat.updatedAt = new Date().toISOString()
        this.persistChatHistory()
      },
      updateCurrentChatTitle() {
        const chat = this.currentChat()
        if (!chat || chat.title !== '新对话') return
        const firstUserMessage = chat.messages.find((message) => message.role === 'user' && message.content)
        if (firstUserMessage) {
          chat.title = firstUserMessage.content.slice(0, 30)
        }
      },
      newChat() {
        const chat = {
          id: 'chat_' + Date.now(),
          title: '新对话',
          messages: [],
          updatedAt: new Date().toISOString()
        }
        this.chatHistory.unshift(chat)
        this.currentChatId = chat.id
        this.chatMessages = chat.messages
        this.chatInput = ''
        this.chatHistoryQuery = ''
        this.persistChatHistory()
        this.scrollChat()
      },
      selectChat(id) {
        const chat = this.chatHistory.find((item) => item.id === id)
        if (!chat) return
        this.currentChatId = id
        this.chatMessages = chat.messages
        this.chatInput = ''
        this.scrollChat()
      },
      async sendChat() {
        const text = this.chatInput.trim()
        if (!text || this.chatBusy) return
        this.chatMessages.push({ role: 'user', content: text })
        const message = { role: 'assistant', content: '', streaming: true }
        this.chatMessages.push(message)
        this.chatInput = ''
        this.chatBusy = true
        this.updateCurrentChatTitle()
        this.touchCurrentChat()
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
          this.touchCurrentChat()
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
  }).mount('#app')
})()
